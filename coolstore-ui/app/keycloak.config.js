var config =
{
  "auth-server-url" : process.env.SSO_URL || 'https://ssoserver/auth',
  "realm": process.env.SSO_REALM || 'arealm',
  "realm-public-key": process.env.SSO_PUBLIC_KEY || '',
  "resource": process.env.SSO_CLIENT_ID || 'aclientid',
  "ssl-required": 'external',
  "public-client": true,
  "enable-cors": true
};

module.exports = config;