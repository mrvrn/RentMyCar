const fs = require('node:fs');
const path = require('node:path');

const event = (listen, code) => ({ listen, script: { type: 'text/javascript', exec: code.trim().split('\n') } });
function request(name, method, route, { role, body, status = 200, pre = '', tests = '', description = '' } = {}) {
  return {
    name,
    request: {
      method, url: '{{baseUrl}}' + route, description,
      auth: role ? { type: 'bearer', bearer: [{ key: 'token', value: `{{${role}Token}}`, type: 'string' }] } : { type: 'noauth' },
      header: body ? [{ key: 'Content-Type', value: 'application/json' }] : [],
      ...(body ? { body: { mode: 'raw', raw: JSON.stringify(body, null, 2).replace(/"__(\w+)__"/g, '{{$1}}'), options: { raw: { language: 'json' } } } } : {})
    },
    event: [
      ...(pre ? [event('prerequest', pre)] : []),
      event('test', `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));
if (pm.response.code !== ${status}) {
  pm.execution.setNextRequest(null);
} else {
${tests}
}`)
    ]
  };
}
const id = key => `Number(pm.collectionVariables.get('${key}'))`;
function login(role, expectedRole) {
  return request(`Log in as ${role} and save ${role}Token`, 'POST', '/login', {
    body: { email: `{{${role}Email}}`, password: `{{${role}Password}}` },
    pre: `pm.collectionVariables.unset('${role}Token');`,
    tests: `const data = pm.response.json();
pm.test('${expectedRole} role', () => pm.expect(data.role).to.eql('${expectedRole}'));
pm.test('JWT returned', () => pm.expect(data.token).to.be.a('string').and.match(/^[^.]+\\.[^.]+\\.[^.]+$/));
if (data.role !== '${expectedRole}') {
  console.error('Owner setup is required: run setup-happy-flow.ps1 and import its environment before running.');
  pm.execution.setNextRequest(null);
} else {
  const payload = JSON.parse(require('buffer').Buffer.from(data.token.split('.')[1], 'base64').toString());
  pm.collectionVariables.set('${role}Token', data.token);
  pm.collectionVariables.set('${role}Id', payload.userId);
}`
  });
}
function details(role, status) {
  return request(`View ${status.toLowerCase()} reservation as ${role}`, 'GET', '/rentals/{{rentalId}}', {
    role,
    tests: `const data = pm.response.json();
pm.test('Correct reservation, car, renter and status', () => {
  pm.expect(data.id).to.eql(${id('rentalId')});
  pm.expect(data.car.id).to.eql(${id('carId')});
  pm.expect(data.renterId).to.eql(${id('renterId')});
  pm.expect(data.status).to.eql('${status}');
  pm.expect(Number(data.totalPrice)).to.eql(25);
});`
  });
}
const variables = {
  baseUrl: 'http://localhost:8080', ownerEmail: '', ownerPassword: '', pickupId: '', dropOffId: '',
  renterEmail: '', renterPassword: 'PostmanHappy123!', ownerToken: '', renterToken: '', ownerId: '', renterId: '',
  carId: '', rentalId: '', licensePlate: '', startAt: '', endAt: '', cancelAfterFlow: 'false'
};
const collection = {
  info: {
    name: 'RentMyCar — Owner and renter happy flows',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: 'Run folders 01–04 in order with Collection Runner (one iteration). Owner creates and publishes a car; a new renter books it; owner confirms; renter sees confirmation. Run setup-happy-flow.ps1 once and import its local environment, or supply an existing OWNER and location IDs. Public registration only creates RENTERS. Folder 05 is optional and skips unless cancelAfterFlow=true. Each run creates persistent fixtures. See HAPPY-FLOWS.md.'
  },
  variable: Object.entries(variables).map(([key, value]) => ({ key, value, type: 'string' })),
  item: [
    {
      name: '01 Owner flow — create a car and allow rentals',
      description: 'Requires an OWNER account and existing location IDs. Setup script provisions both locally. This stage creates a car with availability off, then publishes it.',
      item: [
        { ...login('owner', 'OWNER'), event: [event('prerequest', `
for (const key of ['ownerEmail', 'ownerPassword', 'pickupId', 'dropOffId']) {
  if (!pm.variables.get(key)) {
    pm.execution.setNextRequest(null);
    throw new Error('Missing ' + key + ': run setup-happy-flow.ps1 and import the generated environment, or set owner credentials and location IDs.');
  }
}
for (const key of ['ownerToken', 'renterToken', 'ownerId', 'renterId', 'carId', 'rentalId', 'renterEmail', 'licensePlate', 'startAt', 'endAt']) pm.collectionVariables.unset(key);
pm.collectionVariables.set('licensePlate', 'HAPPY-' + pm.variables.replaceIn('{{$guid}}').replace(/-/g, '').toUpperCase());
`), ...login('owner', 'OWNER').event.filter(e => e.listen === 'test')] },
        request('Check owner profile', 'GET', '/users/{{ownerId}}', { role: 'owner', tests: `
const data = pm.response.json();
pm.test('Owner identity and role', () => {
  pm.expect(data.id).to.eql(${id('ownerId')});
  pm.expect(data.role).to.eql('OWNER');
  pm.expect(data).not.to.have.property('password');
});` }),
        request('Create a car — not yet available', 'POST', '/cars', {
          role: 'owner', status: 201,
          body: { brand: 'Toyota', type: 'Yaris', licensePlate: '{{licensePlate}}', pricePerHour: 12.50, pickupId: '__pickupId__', dropOffId: '__dropOffId__', pictures: [], isAvailable: false },
          tests: `const data = pm.response.json();
pm.test('Car created for this owner, availability off', () => {
  pm.expect(data.id).to.be.a('number').and.above(0);
  pm.expect(data.ownerId).to.eql(${id('ownerId')});
  pm.expect(data.licensePlate).to.eql(pm.collectionVariables.get('licensePlate'));
  pm.expect(data.pricePerHour).to.eql(12.5);
  pm.expect(data.isAvailable).to.eql(false);
});
pm.collectionVariables.set('carId', data.id);`
        }),
        request('Allow the car to be rented', 'PUT', '/cars/{{carId}}', {
          role: 'owner', body: { isAvailable: true },
          tests: `const data = pm.response.json();
pm.test('Correct car is available', () => {
  pm.expect(data.id).to.eql(${id('carId')});
  pm.expect(data.isAvailable).to.eql(true);
});`
        })
      ]
    },
    {
      name: '02 Renter flow — create account, log in and reserve',
      item: [
        request('Register a new renter', 'POST', '/register', {
          body: { name: 'Happy Flow Renter', email: '{{renterEmail}}', phone: '+31612345678', address: 'Teststraat 2, Amsterdam', password: '{{renterPassword}}' },
          pre: `pm.collectionVariables.set('renterEmail', 'happy.renter.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
pm.collectionVariables.unset('renterToken');
pm.collectionVariables.unset('renterId');
pm.collectionVariables.unset('rentalId');`,
          tests: `pm.test('Registration acknowledged', () => pm.expect(pm.response.text()).to.eql('Registration request processed.'));`
        }),
        login('renter', 'RENTER'),
        request('Check renter profile', 'GET', '/users/{{renterId}}', { role: 'renter', tests: `
const data = pm.response.json();
pm.test('Renter identity and role', () => {
  pm.expect(data.id).to.eql(${id('renterId')});
  pm.expect(data.role).to.eql('RENTER');
  pm.expect(data).not.to.have.property('password');
});` }),
        request('Browse cars and find the published car', 'GET', '/cars', { tests: `
pm.test('Published car is listed', () => {
  const car = pm.response.json().find(car => car.id === ${id('carId')});
  pm.expect(car).to.be.an('object');
  pm.expect(car.isAvailable).to.eql(true);
});` }),
        request('View the car before booking', 'GET', '/cars/{{carId}}', { tests: `
const data = pm.response.json();
pm.test('Available car costs 12.50 per hour', () => {
  pm.expect(data.id).to.eql(${id('carId')});
  pm.expect(data.isAvailable).to.eql(true);
  pm.expect(data.pricePerHour).to.eql(12.5);
});` }),
        request('Reserve the car for two hours', 'POST', '/cars/{{carId}}/rental', {
          role: 'renter', status: 201,
          body: { carId: '__carId__', startAt: '{{startAt}}', endAt: '{{endAt}}' },
          pre: `const day = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
pm.collectionVariables.set('startAt', day + 'T10:00:00');
pm.collectionVariables.set('endAt', day + 'T12:00:00');
pm.collectionVariables.unset('rentalId');`,
          tests: `const data = pm.response.json();
pm.test('Pending rental for this renter and car, total 25.00', () => {
  pm.expect(data.id).to.be.a('number').and.above(0);
  pm.expect(data.status).to.eql('PENDING');
  pm.expect(data.carId).to.eql(${id('carId')});
  pm.expect(data.renterId).to.eql(${id('renterId')});
  pm.expect(Number(data.totalPrice)).to.eql(25);
  pm.expect(data.startAt.slice(0, 16)).to.eql(pm.collectionVariables.get('startAt').slice(0, 16));
  pm.expect(data.endAt.slice(0, 16)).to.eql(pm.collectionVariables.get('endAt').slice(0, 16));
});
pm.collectionVariables.set('rentalId', data.id);`
        }),
        details('renter', 'PENDING')
      ]
    },
    {
      name: '03 Owner flow — approve the reservation',
      description: 'The owner checks the reservation by its saved ID, then confirms it. GET /rentals returns personal renter bookings, not incoming owner requests.',
      item: [
        login('owner', 'OWNER'),
        details('owner', 'PENDING'),
        request('Approve the reservation', 'PATCH', '/rentals/{{rentalId}}/status', {
          role: 'owner', body: { status: 'CONFIRMED' },
          tests: `const data = pm.response.json();
pm.test('Reservation confirmed for the correct renter and car', () => {
  pm.expect(data.id).to.eql(${id('rentalId')});
  pm.expect(data.carId).to.eql(${id('carId')});
  pm.expect(data.renterId).to.eql(${id('renterId')});
  pm.expect(data.status).to.eql('CONFIRMED');
  pm.expect(Number(data.totalPrice)).to.eql(25);
});`
        })
      ]
    },
    {
      name: '04 Renter flow — see the confirmed booking',
      item: [
        login('renter', 'RENTER'),
        details('renter', 'CONFIRMED'),
        request('Find the confirmed booking in my rentals', 'GET', '/rentals', { role: 'renter', tests: `
pm.test('My list includes this confirmed reservation', () => {
  const rental = pm.response.json().find(rental => rental.id === ${id('rentalId')});
  pm.expect(rental).to.be.an('object');
  pm.expect(rental.car.id).to.eql(${id('carId')});
  pm.expect(rental.status).to.eql('CONFIRMED');
  pm.expect(Number(rental.totalPrice)).to.eql(25);
});` })
      ]
    },
    {
      name: '05 Optional renter flow — cancel the booking',
      description: 'Skipped by default. Set cancelAfterFlow=true to finish with a cancelled booking instead of a confirmed one.',
      item: [request('Cancel the confirmed booking (optional)', 'PATCH', '/rentals/{{rentalId}}/cancel', {
        role: 'renter',
        pre: `if (String(pm.variables.get('cancelAfterFlow')).toLowerCase() !== 'true') pm.execution.skipRequest();`,
        tests: `const data = pm.response.json();
pm.test('Reservation cancelled with timestamp', () => {
  pm.expect(data.id).to.eql(${id('rentalId')});
  pm.expect(data.status).to.eql('CANCELLED');
  pm.expect(data.cancelledAt).to.be.a('string').and.not.empty;
});`
      })]
    }
  ]
};
fs.writeFileSync(path.join(__dirname, 'RentMyCar-Happy-Flows.postman_collection.json'), JSON.stringify(collection, null, 2) + '\n');
