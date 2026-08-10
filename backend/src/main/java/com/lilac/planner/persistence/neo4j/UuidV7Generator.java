package com.lilac.planner.persistence.neo4j;

import com.lilac.planner.util.Uuids;
import org.springframework.data.neo4j.core.schema.IdGenerator;

public class UuidV7Generator implements IdGenerator<String> {

    @Override
    public String generateId(String primaryLabel, Object entity) {
        return Uuids.v7();
    }
}
