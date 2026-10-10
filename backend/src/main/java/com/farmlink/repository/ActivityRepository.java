package com.farmlink.repository;

import com.farmlink.entity.ActivityEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityRepository extends MongoRepository<ActivityEvent, String> {

    List<ActivityEvent> findTop20ByOrderByTimestampDesc();
}
