define([], function () {
  'use strict';

  const DISTRICT_SOURCE = 'https://raw.githubusercontent.com/iaseth/data-for-india/master/data/readable/districts.json';
  const POSTAL_DIRECTORY_SOURCE = 'js/data/india-postal-directory.json';
  const PIN_SOURCE = 'https://api.postalpincode.in/pincode/';
  const DISTRICT_CACHE_KEY = 'moneybags.india.districts.v1';
  const PIN_CACHE_KEY = 'moneybags.india.pins.v1.';
  const fallback = {
    Karnataka: ['Bengaluru Rural', 'Bengaluru Urban', 'Belagavi', 'Mysuru', 'Udupi'],
    Maharashtra: ['Mumbai City', 'Mumbai Suburban', 'Nagpur', 'Pune', 'Thane'],
    'Tamil Nadu': ['Chennai', 'Coimbatore', 'Madurai', 'Salem', 'Tiruchirappalli'],
    Telangana: ['Hyderabad', 'Karimnagar', 'Nalgonda', 'Rangareddy', 'Warangal'],
    'Uttar Pradesh': ['Agra', 'Ghaziabad', 'Kanpur Nagar', 'Lucknow', 'Varanasi'],
    'West Bengal': ['Darjeeling', 'Howrah', 'Kolkata', 'North 24 Parganas', 'South 24 Parganas'],
  };
  const allStates = ['Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'National Capital Territory of Delhi', 'Puducherry'];
  let districtData = fallback;
  let postalData = {};
  let loadPromise;

  const normalize = (value) => String(value || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  const alias = {
    delhi: 'nationalcapitalterritoryofdelhi',
    orissa: 'odisha',
    pondicherry: 'puducherry',
  };
  const same = (left, right) => {
    const a = alias[normalize(left)] || normalize(left);
    const b = alias[normalize(right)] || normalize(right);
    return a === b;
  };
  const cache = {
    get: (key) => { try { return JSON.parse(sessionStorage.getItem(key) || 'null'); } catch (e) { return null; } },
    set: (key, value) => { try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* Session storage is optional. */ } },
  };

  function grouped(records) {
    return records.reduce((result, record) => {
      if (!record.state || !record.district) return result;
      result[record.state] = result[record.state] || [];
      result[record.state].push(record.district);
      return result;
    }, {});
  }

  function keyFor(data, value) {
    return Object.keys(data).find((key) => same(key, value));
  }

  function localAreas(state, district) {
    const stateKey = keyFor(postalData, state);
    const districtKey = stateKey && keyFor(postalData[stateKey], district);
    return stateKey && districtKey ? postalData[stateKey][districtKey] : [];
  }

  function load() {
    if (loadPromise) return loadPromise;
    loadPromise = Promise.resolve().then(async () => {
      const saved = cache.get(DISTRICT_CACHE_KEY);
      if (saved && Object.keys(saved).length) {
        districtData = saved;
      }
      const [districtResult, postalResult] = await Promise.allSettled([
        fetch(DISTRICT_SOURCE).then((response) => response.ok ? response.json() : Promise.reject(new Error('District directory is unavailable.'))),
        fetch(POSTAL_DIRECTORY_SOURCE).then((response) => response.ok ? response.json() : Promise.reject(new Error('Postal directory is unavailable.'))),
      ]);
      if (districtResult.status === 'fulfilled') districtData = grouped(districtResult.value.districts || []);
      if (postalResult.status === 'fulfilled' && postalResult.value && postalResult.value.states) postalData = postalResult.value.states;
      cache.set(DISTRICT_CACHE_KEY, districtData);
      return districtData;
    }).catch(() => districtData);
    return loadPromise;
  }

  async function validatePincode(state, district, pincode) {
    const pin = String(pincode || '').trim();
    if (!/^\d{6}$/.test(pin) || !state || !district) return false;
    const local = localAreas(state, district);
    if (local.length) return local.some((entry) => String(entry.pincode) === pin);
    const cacheKey = PIN_CACHE_KEY + pin;
    let offices = cache.get(cacheKey);
    if (!offices) {
      const response = await fetch(PIN_SOURCE + encodeURIComponent(pin));
      if (!response.ok) throw new Error('PIN lookup is unavailable.');
      const result = await response.json();
      offices = result && result[0] && result[0].Status === 'Success' ? result[0].PostOffice : [];
      cache.set(cacheKey, offices);
    }
    return Array.isArray(offices) && offices.some((office) =>
      same(office.State, state) && same(office.District, district));
  }

  return {
    load,
    states: () => Array.from(new Set(allStates.concat(Object.keys(districtData), Object.keys(postalData)))).sort(),
    districts: (state) => {
      const stateKey = keyFor(postalData, state);
      const postalDistricts = stateKey ? Object.keys(postalData[stateKey]) : [];
      return Array.from(new Set((districtData[state] || []).concat(postalDistricts))).sort();
    },
    areas: (state, district) => Array.from(new Set(localAreas(state, district).map((entry) => entry.area))).sort(),
    pincodes: (state, district, area) => Array.from(new Set(localAreas(state, district)
      .filter((entry) => same(entry.area, area)).map((entry) => String(entry.pincode)))).sort(),
    validatePincode,
  };
});
