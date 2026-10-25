'use strict';
function haversineKm(lat1, lon1, lat2, lon2) {
  const r = 6371, rad = Math.PI / 180, dLat = (lat2 - lat1) * rad, dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(a));
}
/** Spot coordinates are district-level, so a check-in counts if the phone is within this distance. */
const CHECKIN_RADIUS_KM = 15;
module.exports = { haversineKm, CHECKIN_RADIUS_KM };
