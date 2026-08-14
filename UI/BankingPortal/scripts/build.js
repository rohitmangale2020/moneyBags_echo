'use strict';

const tooling = require('@oracle/oraclejet-tooling');

tooling
  .build('web', { buildType: 'release' })
  .then(() => console.log('Oracle JET web build completed.'))
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
