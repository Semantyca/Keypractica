package io.kneo.projects.repository;

import io.kneo.core.model.Language;
import io.kneo.core.model.constants.ProjectStatusType;
import io.kneo.core.model.embedded.RLS;
import io.kneo.core.repository.AsyncRepository;
import io.kneo.projects.model.Project;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProjectRepository extends AsyncRepository {

    public Uni<List<Project>> getAll(final int limit, final int offset, final long userID) {
        String sql = "SELECT * FROM prj__projects p, prj__project_readers ppr WHERE p.id = ppr.entity_id AND ppr.reader = " + userID;
        if (limit > 0) {
           sql += String.format(" LIMIT %s OFFSET %s", limit, offset);
        }
        return client.query(sql)
                .execute()
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(this::from)
                .collect().asList();
    }

    public Uni<Integer> getAllCount(long userID) {
        return getAllCount(userID, "prj__projects", "prj__project_readers");
    }

    public Uni<Project> findById(UUID uuid, Long userID) {
        return client.preparedQuery("SELECT * FROM prj__projects p, prj__project_readers ppr WHERE p.id = ppr.entity_id  AND p.id = $1 AND ppr.reader = $2")
                .execute(Tuple.of(uuid, userID))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public Uni<List<RLS>> getAllReaders(UUID uuid) {
        return client.preparedQuery("SELECT reader, reading_time, can_edit, can_delete FROM prj__projects p, prj__project_readers ppr WHERE p.id = ppr.entity_id AND p.id = $1")
                .execute(Tuple.of(uuid))
                .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
                .onItem().transform(row -> new RLS(
                        Optional.ofNullable(row.getLocalDateTime("reading_time"))
                                .map(dateTime -> ZonedDateTime.from(dateTime.atZone(ZoneId.systemDefault())))
                                .orElse(null),
                        row.getLong("reader"),
                        row.getLong("can_edit"),
                        row.getLong("can_delete")))
                .collect().asList();
    }

    private Project from(Row row) {
        return new Project.Builder()
                .setId(row.getUUID("id"))
                .setName(row.getString("name"))
                .setStatus(ProjectStatusType.valueOf(row.getString("status")))
                .setFinishDate(row.getLocalDate("finish_date"))
                .setPosition(999)
                .setPrimaryLang(new Language.Builder().build())
                .setManager(row.getInteger("manager"))
                .setCoder(row.getInteger("programmer"))
                .setTester(row.getInteger("tester"))
                .build();
    }


    public UUID insert(Project node, Long user) {

        return node.getId();
    }


    public Language update(Language node) {

        return node;
    }

    public int delete(Long id) {

        return 1;
    }

}
