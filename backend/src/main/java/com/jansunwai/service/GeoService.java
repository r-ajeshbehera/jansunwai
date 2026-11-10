package com.jansunwai.service;

import com.jansunwai.repository.GeoRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GeoService {
    private final GeoRepository geo;

    public GeoService(GeoRepository geo) {
        this.geo = geo;
    }

    /** Shown to citizens BEFORE they submit: "3 open road complaints within 200 m" (counts only, no details). */
    @Transactional(readOnly = true)
    public List<GeoRepository.NearbyCount> nearbySummary(double lat, double lng, int radiusMeters) {
        if (radiusMeters < 10 || radiusMeters > 2000) throw new IllegalArgumentException("radiusMeters must be between 10 and 2000");
        return geo.openNearbyByCategory(lat, lng, radiusMeters);
    }

    @Transactional(readOnly = true)
    public List<GeoRepository.HeatPoint> heatmap(Long wardId, Long departmentId, AppUserPrincipal user) {
        Long dept = user.isAdmin() ? departmentId : user.departmentId();
        return geo.heatmap(wardId, dept);
    }

    @Transactional(readOnly = true)
    public List<GeoRepository.Hotspot> hotspots() {
        return geo.hotspots(250, 2);
    }

    @Transactional(readOnly = true)
    public List<GeoRepository.WardShape> wardShapes() {
        return geo.wardShapes();
    }
}
