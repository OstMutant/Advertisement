package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.entities.AdvertisementAttachment;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvertisementAttachmentRepository extends CrudRepository<AdvertisementAttachment, Long> {

    List<AdvertisementAttachment> findByAdvertisementId(Long advertisementId);

}