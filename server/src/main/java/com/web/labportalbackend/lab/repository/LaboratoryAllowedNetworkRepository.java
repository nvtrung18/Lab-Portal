package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.lab.entity.LaboratoryAllowedNetwork;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LaboratoryAllowedNetworkRepository extends JpaRepository<LaboratoryAllowedNetwork, Long> {

    List<LaboratoryAllowedNetwork> findByLaboratoryIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAscIdAsc(Long laboratoryId);

    Optional<LaboratoryAllowedNetwork> findByIdAndLaboratoryIdAndDeletedFalse(Long id, Long laboratoryId);

    Optional<LaboratoryAllowedNetwork> findFirstByLaboratoryIdAndNetworkValueOrderByIdDesc(
            Long laboratoryId, String networkValue);
}
