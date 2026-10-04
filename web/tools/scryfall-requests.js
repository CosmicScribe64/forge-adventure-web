/* For webtest's js step: every Scryfall request so far, as [start ms, duration ms, HTTP status,
   initiator, URL]. Requests to api.scryfall.com should be at least 100 ms apart. */
JSON.stringify(performance.getEntriesByType('resource').filter(e => e.name.includes('scryfall')).map(e => [Math.round(e.startTime), Math.round(e.duration), e.responseStatus || 0, e.initiatorType, e.name.replace(/^https:\/\//, '').slice(0, 90)]))
