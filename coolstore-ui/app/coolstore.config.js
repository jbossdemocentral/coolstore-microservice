var config =
{
  API_ENDPOINT: process.env.API_ENDPOINT,
  SECURE_API_ENDPOINT: process.env.SECURE_API_ENDPOINT,
  SSO_ENABLED: process.env.SSO_URL ? true : false
};

module.exports = config;