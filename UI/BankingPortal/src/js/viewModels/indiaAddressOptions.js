define([], function () {
  'use strict';

  const DISTRICT_SOURCE = 'https://raw.githubusercontent.com/iaseth/data-for-india/master/data/readable/districts.json';
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

  function load() {
    if (loadPromise) return loadPromise;
    loadPromise = Promise.resolve().then(async () => {
      const saved = cache.get(DISTRICT_CACHE_KEY);
      if (saved && Object.keys(saved).length) {
        districtData = saved;
        return districtData;
      }
      const response = await fetch(DISTRICT_SOURCE);
      if (!response.ok) throw new Error('District directory is unavailable.');
      const json = await response.json();
      districtData = grouped(json.districts || []);
      cache.set(DISTRICT_CACHE_KEY, districtData);
      return districtData;
    }).catch(() => districtData);
    return loadPromise;
  }

  async function validatePincode(state, district, pincode) {
    const pin = String(pincode || '').trim();
    if (!/^\d{6}$/.test(pin) || !state || !district) return false;
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
    states: () => Array.from(new Set(allStates.concat(Object.keys(districtData)))).sort(),
    districts: (state) => (districtData[state] || []).slice().sort(),
    validatePincode,
  };
});
