'use strict';
const fs = require('node:fs');
const path = require('node:path');

const raw = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'destinations.json'), 'utf8'));

const INTERESTS = ['beach', 'hills', 'nature', 'food', 'culture', 'adventure'];
const DISTRICTS = raw.districts;
const BASE_DESTINATIONS = raw.destinations;
const ACTIVITIES = raw.activities;

// Same rough bounding box for Bangladesh used to sanity-check submitted coordinates.
const BOUNDS = { minLat: 20.5, maxLat: 26.7, minLon: 88.0, maxLon: 92.8 };

module.exports = { INTERESTS, DISTRICTS, BASE_DESTINATIONS, ACTIVITIES, BOUNDS };
