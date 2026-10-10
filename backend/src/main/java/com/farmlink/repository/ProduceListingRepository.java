package com.farmlink.repository;

import com.farmlink.entity.ListingStatus;
import com.farmlink.entity.ProduceListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProduceListingRepository extends JpaRepository<ProduceListing, Long> {

    List<ProduceListing> findByFarmerIdOrderByAvailableFromAsc(Long farmerId);

    long countByStatus(ListingStatus status);

    @Query("""
            select l from ProduceListing l
              where l.status = :status
                and (:search = '' or lower(l.name) like lower(concat('%', :search, '%'))
                                 or lower(l.category) like lower(concat('%', :search, '%'))
                                 or lower(l.location) like lower(concat('%', :search, '%')))
                and (:category = '' or lower(l.category) = lower(:category))
                and (:location = '' or lower(l.location) like lower(concat('%', :location, '%')))
            order by l.availableFrom asc, l.id asc
            """)
    List<ProduceListing> search(@Param("status") ListingStatus status,
                                @Param("search") String search,
                                @Param("category") String category,
                                @Param("location") String location);

    @Query("select distinct l.category from ProduceListing l where l.status = :status order by l.category")
    List<String> findCategoriesByStatus(@Param("status") ListingStatus status);

    @Query("select distinct l.location from ProduceListing l where l.status = :status order by l.location")
    List<String> findLocationsByStatus(@Param("status") ListingStatus status);
}
