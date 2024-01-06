package io.kneo.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.user.Role;
import io.kneo.core.repository.table.EntityData;
import io.kneo.core.repository.table.TableNameResolver;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.kneo.core.repository.table.TableNameResolver.ROLE_ENTITY_NAME;


@ApplicationScoped
public class RoleRepository extends AsyncRepository {
    private static final EntityData ENTITY_DATA = TableNameResolver.create().getEntityNames(ROLE_ENTITY_NAME);
    @Inject
    PgPool client;
    @Inject
    ObjectMapper mapper;

    private static final Logger LOGGER = LoggerFactory.getLogger("RoleRepository");

    public Uni<List<Role>> getAll(final int limit, final int offset) {
        String sql = "SELECT * FROM _roles";
        if (limit > 0 ) {
            sql += String.format(" LIMIT %s OFFSET %s", limit, offset);
        }
        return client.query(sql)
                .execute()
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(this::from).collect().asList();
    }

    public Uni<Integer> getAllCount() {
        return getAllCount(ENTITY_DATA.mainName());
    }

    public Uni<Optional<Role>> findById(UUID uuid) {
        return client.preparedQuery("SELECT * FROM _roles sr WHERE sr.id = $1")
                .execute(Tuple.of(uuid))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? Optional.of(from(iterator.next())) : Optional.empty());
    }

    public Uni<Optional<Role>> findByUserId(long id) {
        return client.preparedQuery("SELECT * FROM _roles sr WHERE sr.user_id = $1")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? Optional.of(from(iterator.next())) : Optional.empty());
    }

    private Role from(Row row) {
        EnumMap<LanguageCode, String> map;
        try {
            Object o =  row.getJsonObject("loc_name");
            if (o != null) {
                map = mapper.readValue(row.getJsonObject("loc_name").toString(), new TypeReference<>() {
                });
            } else {
                map = new EnumMap<>(LanguageCode.class);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
        return new Role.Builder()
                .setId(row.getUUID("id"))
                .setAuthor(row.getLong("author"))
                .setRegDate(row.getLocalDateTime("reg_date").atZone(ZoneId.systemDefault()))
                .setLastModifier(row.getLong("last_mod_user"))
                .setLastModifiedDate(row.getLocalDateTime("last_mod_date").atZone(ZoneId.systemDefault()))
                .setIdentifier(row.getString("identifier"))
                .setLocalizedName(map)
                .build();
    }

    public Uni<UUID> insert(Role doc, Long user) {
        LocalDateTime nowTime = ZonedDateTime.now().toLocalDateTime();
        String sql = String.format("INSERT INTO %s (author, identifier, reg_date, last_mod_date, last_mod_user, loc_name, loc_descr) VALUES($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id", ENTITY_DATA.mainName());
        Tuple params = Tuple.of(user, doc.getIdentifier(), nowTime, nowTime, user);
        Tuple finalParams = params.addJsonObject(JsonObject.mapFrom(doc.getLocalizedName())).addJsonObject(JsonObject.mapFrom(doc.getLocalizedDescription()));
        return client.withTransaction(tx -> tx.preparedQuery(sql)
                .execute(finalParams)
                .onItem().transform(result -> result.iterator().next().getUUID("id"))
                .onFailure().recoverWithUni(throwable -> {
                    LOGGER.error(throwable.getMessage());
                    return Uni.createFrom().failure(new RuntimeException(String.format("Failed to insert to %s", ROLE_ENTITY_NAME), throwable));
                }));
    }


    public Uni<Integer> update(Role doc, long user) {
        LocalDateTime nowTime = ZonedDateTime.now().toLocalDateTime();
        String sql = String.format("UPDATE %s SET identifier=$1, last_mod_date=$2, last_mod_user=$3, loc_name=$4, localized_descr=$5 WHERE id=$6", ENTITY_DATA.mainName());
        Tuple params = Tuple.of(doc.getIdentifier(), nowTime, user, JsonObject.mapFrom(doc.getLocalizedName()), JsonObject.mapFrom(doc.getLocalizedDescription()));
        Tuple finalParams = params.addUUID(doc.getId());
        return client.withTransaction(tx -> tx.preparedQuery(sql)
                .execute(finalParams)
                .onItem().transform(result -> result.rowCount() > 0 ? 1 : 0)
                .onFailure().recoverWithUni(throwable -> {
                    LOGGER.error(throwable.getMessage());
                    return Uni.createFrom().item(0);
                }));
    }

    public Uni<Void> delete(UUID uuid) {
        return delete(uuid, ENTITY_DATA.mainName());
    }


}
