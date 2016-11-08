var config =
{
  "auth-server-url" : process.env.SSO_URL,
  "realm": process.env.SSO_REALM,
  "realm-public-key": process.env.SSO_PUBLIC_KEY ,
  "resource": process.env.SSO_CLIENT_ID,
  "ssl-required": 'external',
  "public-client": true,
  "enable-cors": true
};

module.exports = config;