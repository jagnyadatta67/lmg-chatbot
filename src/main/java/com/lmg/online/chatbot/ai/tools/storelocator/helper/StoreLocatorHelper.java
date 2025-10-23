package com.lmg.online.chatbot.ai.tools.storelocator.helper;

import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreLocatorAPIResponse;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreView;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StoreLocatorHelper {

    /**
     * Finds the nearest N stores from StoreLocatorAPIResponse.
     *
     * @param response the response object containing list of stores
     * @param userLat  current latitude
     * @param userLon  current longitude
     * @param limit    number of stores to return
     * @return list of nearest stores in StoreView format
     */
    public static StoreList getNearestStores(
            StoreLocatorAPIResponse response,
            double userLat,
            double userLon,
            int limit) {

        if (response == null || response.getStores() == null || response.getStores().isEmpty()) {
            return new StoreList();
        }

        List<StoreView> storeViewLis= response.getStores().stream()
                .filter(s -> s.getGeoPoint() != null)
                .map(s -> {
                    double lat = s.getGeoPoint().getLatitude();
                    double lon = s.getGeoPoint().getLongitude();
                    double distance = calculateDistance(userLat, userLon, lat, lon);

                    return new StoreView(
                            s.getStoreId(),
                            s.getStoreName(),
                            s.getAddress() != null ? s.getAddress().getCity() : "N/A",
                            s.getAddress() != null ? s.getAddress().getContactNumber() : "N/A",
                            s.getAddress() != null ? s.getAddress().getContactNumber(): "N/A",
                            s.getWorkingHours(),
                            lat,
                            lon,
                            distance,s.getAddress() != null ?s.getAddress().getLine1():"", s.getAddress() != null ?s.getAddress().getLine2():"", s.getAddress() != null ?s.getAddress().getPostalCode():""
                    );
                })
                .sorted(Comparator.comparingDouble(StoreView::getDistance))
                .limit(limit)
                .collect(Collectors.toList());

               StoreList st= new StoreList();
        st.setStores(storeViewLis);
        return st;
    }

    /**
     * Haversine formula to calculate distance (in km) between two lat/lon points.
     */
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
