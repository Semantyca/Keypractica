package io.kneo.qtracker.service;

import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.UserRepository;
import io.kneo.core.service.AbstractService;
import io.kneo.core.service.UserService;
import io.kneo.qtracker.dto.ConsumingCalcDTO;
import io.kneo.qtracker.dto.ConsumingDTO;
import io.kneo.qtracker.model.Consuming;
import io.kneo.qtracker.model.Image;
import io.kneo.qtracker.repository.ConsumingRepository;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConsumingService extends AbstractService<Consuming, ConsumingDTO> {
    private final ConsumingRepository repository;

    Validator validator;

    @Inject
    public ConsumingService(UserRepository userRepository, UserService userService, Validator validator, ConsumingRepository repository) {
        super(userRepository, userService);
        this.validator = validator;
        this.repository = repository;
    }

    public Uni<List<ConsumingDTO>> getAll(int limit, int offset, IUser user) {
        assert repository != null;
        Uni<List<Consuming>> uni = repository.getAll(limit, offset, user);
        return uni
                .onItem().transform(consumingList -> consumingList.stream()
                        .map(consuming -> ConsumingDTO.builder()
                                .id(consuming.getId())
                                .vehicleId(consuming.getVehicleId())
                                .totalKm(consuming.getTotalKm())
                                .lastLiters(consuming.getLastLiters())
                                .lastCost(consuming.getLastCost())
                                .build())
                        .collect(Collectors.toList()));
    }

    public Uni<Integer> getAllCount(IUser user) {
        assert repository != null;
        return repository.getAllCount(user);
    }

    public Uni<List<ConsumingDTO>> getAllMine(String telegramName, IUser user) {
        assert repository != null;
        Uni<List<Consuming>> uni = repository.getAllMine(100, 0, telegramName, user);
        return uni
                .onItem().transform(consumingList -> consumingList.stream()
                        .map(consuming -> ConsumingDTO.builder()
                                .id(consuming.getId())
                                .regDate(consuming.getRegDate())
                                .vehicleId(consuming.getVehicleId())
                                .totalKm(consuming.getTotalKm())
                                .lastLiters(consuming.getLastLiters())
                                .lastCost(consuming.getLastCost())
                                .build())
                        .collect(Collectors.toList()));
    }


    @Override
    public Uni<ConsumingDTO> getDTO(UUID uuid, IUser user, LanguageCode code) {
        assert repository != null;
        Uni<Consuming> consumingUni = repository.findById(uuid);
        return consumingUni.onItem().transformToUni(this::map);
    }

    public Uni<Consuming> getById(UUID uuid, IUser user) {
        assert repository != null;
        return repository.findById(uuid);
    }

    @Override
    public Uni<ConsumingDTO> upsert(String id, ConsumingDTO dto, IUser user, LanguageCode code) {
        assert repository != null;
        Tuple2<Consuming, List<Image>> entityTuple = buildEntity(dto);
        Consuming consuming = entityTuple.getItem1();
        List<Image> images = entityTuple.getItem2();

        if (id == null) {
            return repository.insert(consuming, user, images)
                    .onItem().transformToUni(this::map);
        } else {
            return repository.update(UUID.fromString(id), consuming, user)
                    .onItem().transformToUni(this::map);
        }
    }

    public Uni<ConsumingCalcDTO> insertAndProcess(String id, ConsumingDTO dto, IUser user, LanguageCode code) {
        assert repository != null;
        Tuple2<Consuming, List<Image>> entityTuple = buildEntity(dto);
        Consuming consuming = entityTuple.getItem1();
        List<Image> images = entityTuple.getItem2();

        return repository.insert(consuming, user, images)
                .onItem().transformToUni(v -> calcConsuming(v, user));
    }

    private Uni<ConsumingCalcDTO> calcConsuming(Consuming doc, IUser user) {
        return repository.getLastTwo(doc.getVehicleId(), user)
                .map(records -> {
                    ConsumingCalcDTO result = new ConsumingCalcDTO();
                    if (records.size() < 2) {
                        result.setTotalTrip(0);
                        result.setLitersPerHundred(-1);
                        return result;
                    }
                    Consuming lastRecord = records.get(0);
                    Consuming secondLastRecord = records.get(1);

                    double totalTrip = lastRecord.getTotalKm() - secondLastRecord.getTotalKm();
                    double litersPerHundred = (lastRecord.getLastLiters() / totalTrip) * 100;

                    result.setTotalTrip(Math.round(totalTrip * 100.0) / 100.0);
                    result.setLitersPerHundred(Math.round(litersPerHundred * 100.0) / 100.0);


                    return result;
                });
    }



    private Uni<ConsumingDTO> map(Consuming doc) {
        return Uni.createFrom().item(() -> ConsumingDTO.builder()
                .id(doc.getId())
                .vehicleId(doc.getVehicleId())
                .totalKm(doc.getTotalKm())
                .lastLiters(doc.getLastLiters())
                .lastCost(doc.getLastCost())
                .build());
    }

    private Tuple2<Consuming, List<Image>> buildEntity(ConsumingDTO dto) {
        Consuming consuming = new Consuming();
        consuming.setVehicleId(dto.getVehicleId());
        consuming.setTotalKm(dto.getTotalKm());
        consuming.setLastLiters(dto.getLastLiters());
        consuming.setLastCost(dto.getLastCost());
        consuming.setAddInfo(dto.getAddInfo());

        List<Image> images = null;
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            images = dto.getImages().stream().map(imageDTO -> {
                Image image = new Image();
                byte[] imageData = Base64.getDecoder().decode(imageDTO.getImageData());
                image.setImageData(imageData);
                image.setType(imageDTO.getType());
                image.setConfidence(imageDTO.getConfidence());
                image.setNumOfSeq(imageDTO.getNumOfSeq());
                image.setAddInfo(imageDTO.getAddInfo());
                image.setDescription(imageDTO.getDescription());

                return image;
            }).collect(Collectors.toList());
        }
        return Tuple2.of(consuming, images);
    }

    public Uni<Integer> delete(String id, IUser user) {
        assert repository != null;
        return repository.delete(UUID.fromString(id), user);
    }
}
