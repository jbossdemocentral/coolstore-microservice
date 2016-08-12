var config =
{
  "auth-server-url" : process.env.SSO_URL || 'https://secure-sso-testsso2.shadowman.com/auth',
  "realm": process.env.SSO_REALM || 'myrealm',
  "realm-public-key": process.env.SSO_PUBLIC_KEY || '',
  "resource": process.env.SSO_CLIENT_ID || 'coolstore-ui',
  "ssl-required": 'external',
  "public-client": true
};

module.exports = config;