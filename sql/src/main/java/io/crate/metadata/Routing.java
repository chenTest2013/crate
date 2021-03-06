package io.crate.metadata;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Routing implements Streamable {

    private Map<String, Map<String, Set<Integer>>> locations;

    public Routing() {

    }

    public Routing(@Nullable Map<String, Map<String, Set<Integer>>> locations) {
        this.locations = locations;
    }

    /**
     * @return a map with the locations in the following format: <p>
     *  Map< nodeName (string), <br />
     *  &nbsp;&nbsp;&nbsp;&nbsp;Map< indexName (string), Set<ShardId (int) > <br />
     *  </p>
     */
    @Nullable
    public Map<String, Map<String, Set<Integer>>> locations() {
        return locations;
    }

    public boolean hasLocations() {
        return locations != null && locations.size() > 0;
    }

    public Set<String> nodes() {
        if (hasLocations()) {
            return locations.keySet();
        }
        return ImmutableSet.of();
    }

    /**
     * get the number of shards in this routing for a node with given nodeId
     * @param nodeId
     * @return int >= 0
     */
    public int numShards(String nodeId) {
        int count = 0;
        if (hasLocations()) {
            Map<String, Set<Integer>> nodeRouting = locations.get(nodeId);
            if (nodeRouting != null) {
                for (Set<Integer> shardIds : nodeRouting.values()) {
                    count += shardIds.size();
                }
            }
        }
        return count;
    }

    /**
     * returns true if the routing contains shards for any table of the given node
     */
    public boolean containsShards(String nodeId) {
        if (!hasLocations()) return false;
        Map<String, Set<Integer>> nodeRouting = locations.get(nodeId);
        if (nodeRouting == null) return false;

        for (Map.Entry<String, Set<Integer>> tableEntry : nodeRouting.entrySet()) {
            if (tableEntry.getValue() != null && !tableEntry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        Objects.ToStringHelper helper = Objects.toStringHelper(this);
        if (hasLocations()) {
            helper.add("locations", locations);
        }
        return helper.toString();

    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        int numLocations = in.readVInt();
        if (numLocations > 0) {
            locations = new HashMap<>(numLocations);

            String nodeId;
            int numInner;
            Map<String, Set<Integer>> innerMap;
            for (int i = 0; i < numLocations; i++) {
                nodeId = in.readOptionalString();
                numInner = in.readVInt();
                innerMap = new HashMap<>(numInner);

                locations.put(nodeId, innerMap);
                for (int j = 0; j < numInner; j++) {
                    String key = in.readString();
                    Set<Integer> shardIds = new HashSet<>();

                    int numShards = in.readVInt();
                    for (int k = 0; k<numShards;k++){
                        shardIds.add(in.readVInt());
                    }
                    innerMap.put(key, shardIds);
                }
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (hasLocations()) {
            out.writeVInt(locations.size());

            for (Map.Entry<String, Map<String, Set<Integer>>> entry : locations.entrySet()) {
                out.writeOptionalString(entry.getKey());

                if (entry.getValue() == null) {
                    out.writeVInt(0);
                } else {

                    out.writeVInt(entry.getValue().size());
                    for (Map.Entry<String, Set<Integer>> innerEntry : entry.getValue().entrySet()) {
                        out.writeString(innerEntry.getKey());
                        Set<Integer> shardIds = innerEntry.getValue();
                        if (shardIds == null || shardIds.size() == 0) {
                            out.writeVInt(0);
                        } else {
                            out.writeVInt(shardIds.size());
                            for (Integer shardId : shardIds) {
                                out.writeVInt(shardId);
                            }
                        }
                    }
                }
            }
        } else {
            out.writeVInt(0);
        }
    }
}
