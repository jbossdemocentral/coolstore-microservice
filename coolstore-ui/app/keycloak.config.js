var pk = 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArUCsfdVvwyo1L9FZyOsioPhKjHXoearkvvQfWraa88xLhw1f69cPsO8iG2d8nUNG5sOuvzJ/2HuO+c3oTxpLkeReYiZFt/KnmJmT0o3QVzq/oJOzKPVCg/nXgcWQBLX4vj9tTQ0rXsinANuk3f2qCNwkFiV2d/ELllyI+qKs+P8NfKSK4VVi8OkuhyMeYr4f8jZXbtdciErW9brnjUUNkoX0QraxIMvLF8rje1JaxUwxHuMVFlBJ9Ix4RBsjOA4/wdd/VhVaH2fnpwPxvz0WcaiH5gxR3aAmPKPSKl44Ru8sQPmDjfscfOBjmyZOCRbF0aj2aQVLyxGiX+LaIVGZWQIDAQAB';


var config =
{
  "auth-server-url" : process.env.SSO_URL || 'https://secure-sso-testsso2.shadowman.com/auth',
  "realm": process.env.SSO_REALM || 'myrealm',
  "realm-public-key": process.env.SSO_PUBLIC_KEY || pk,
  "credentials": {
    "secret": process.env.SSO_SECRET || 'VLMl8QIr'
  },
  "resource": 'coolstore-ui',
  "ssl-required": 'external'
};

module.exports = config;