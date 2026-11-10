package com.jansunwai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** PostGIS queries: which ward contains a point, what is nearby, where are the hotspots. */
@Repository
public class GeoRepository {
    private final JdbcTemplate jdbc;

    public GeoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NearbyCount(String category, long count) { }
    public record HeatPoint(double lat, double lng, double weight) { }
    public record Hotspot(int clusterId, long complaints, double lat, double lng, String topCategory, String wardName) { }
    public record WardShape(long id, String code, String name, String geoJson) { }

    private static final String OPEN = "('APPROVED','ASSIGNED','IN_PROGRESS','RESOLUTION_SUBMITTED','REOPENED','UNDER_REVIEW')";

    /** ST_Contains(polygon, point): the ward whose boundary contains the GPS point. */
    public Optional<Long> wardContaining(BigDecimal lat, BigDecimal lng) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM wards
                WHERE boundary IS NOT NULL AND ST_Contains(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                ORDER BY id LIMIT 1
                """, Long.class, lng.doubleValue(), lat.doubleValue());
        return ids.stream().findFirst();
    }

    /** ST_DWithin on geography = true distance in metres, using the GiST index. Only counts, no personal data. */
    public List<NearbyCount> openNearbyByCategory(double lat, double lng, int radiusMeters) {
        return jdbc.query("SELECT COALESCE(category, 'UNCLASSIFIED'), count(*) FROM complaints " +
                "WHERE status IN " + OPEN + " AND location IS NOT NULL " +
                "AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?) " +
                "GROUP BY 1 ORDER BY 2 DESC",
                (rs, i) -> new NearbyCount(rs.getString(1), rs.getLong(2)), lng, lat, radiusMeters);
    }

    /** Weighted points for a heatmap: HIGH priority and overdue complaints weigh more. */
    public List<HeatPoint> heatmap(Long wardId, Long departmentId) {
        return jdbc.query("SELECT latitude, longitude, " +
                "CASE priority WHEN 'HIGH' THEN 1.0 WHEN 'MEDIUM' THEN 0.6 ELSE 0.35 END " +
                " + CASE WHEN sla_due_at < now() THEN 0.4 ELSE 0 END " +
                "FROM complaints WHERE status IN " + OPEN + " AND latitude IS NOT NULL " +
                "AND (?::bigint IS NULL OR ward_id = ?) AND (?::bigint IS NULL OR department_id = ?)",
                (rs, i) -> new HeatPoint(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)),
                wardId, wardId, departmentId, departmentId);
    }

    /**
     * ST_ClusterDBSCAN groups open complaints that are within eps metres of each other (Web Mercator, approximately
     * metres at this latitude) with at least minPoints members. Each cluster is a recurring-issue hotspot.
     */
    public List<Hotspot> hotspots(int epsMeters, int minPoints) {
        return jdbc.query("""
                WITH pts AS (
                  SELECT c.id, c.category, c.ward_id, c.location::geometry AS g,
                         ST_ClusterDBSCAN(ST_Transform(c.location::geometry, 3857), eps := ?, minpoints := ?) OVER () AS cid
                  FROM complaints c
                  WHERE c.status IN %s AND c.location IS NOT NULL
                )
                SELECT cid, count(*) AS n, ST_Y(ST_Centroid(ST_Collect(g))) AS lat, ST_X(ST_Centroid(ST_Collect(g))) AS lng,
                       mode() WITHIN GROUP (ORDER BY category) AS top_category,
                       (SELECT w.name FROM wards w WHERE w.id = mode() WITHIN GROUP (ORDER BY pts.ward_id)) AS ward_name
                FROM pts WHERE cid IS NOT NULL
                GROUP BY cid ORDER BY n DESC
                """.formatted(OPEN),
                (rs, i) -> new Hotspot(rs.getInt(1), rs.getLong(2), rs.getDouble(3), rs.getDouble(4), rs.getString(5),
                        rs.getString(6)), epsMeters, minPoints);
    }

    public List<WardShape> wardShapes() {
        return jdbc.query("SELECT id, code, name, ST_AsGeoJSON(boundary) FROM wards ORDER BY code",
                (rs, i) -> new WardShape(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    /** Ring of [lng, lat] points -> polygon. ST_IsValid guards against self-intersecting shapes. */
    public void setWardBoundary(long wardId, List<List<Double>> ring) {
        StringBuilder wkt = new StringBuilder("POLYGON((");
        for (int i = 0; i < ring.size(); i++) {
            if (i > 0) wkt.append(',');
            wkt.append(ring.get(i).get(0)).append(' ').append(ring.get(i).get(1));
        }
        if (!ring.get(0).equals(ring.get(ring.size() - 1))) {
            wkt.append(',').append(ring.get(0).get(0)).append(' ').append(ring.get(0).get(1));
        }
        wkt.append("))");
        Boolean valid = jdbc.queryForObject("SELECT ST_IsValid(ST_GeomFromText(?, 4326))", Boolean.class, wkt.toString());
        if (!Boolean.TRUE.equals(valid)) throw new IllegalArgumentException("Ward boundary is not a valid polygon");
        jdbc.update("UPDATE wards SET boundary = ST_GeomFromText(?, 4326) WHERE id = ?", wkt.toString(), wardId);
    }
}
