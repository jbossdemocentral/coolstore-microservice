var config =
{
  API_ENDPOINT: (process.env.COOLSTORE_GW_ENDPOINT != null ? process.env.COOLSTORE_GW_ENDPOINT  : process.env.COOLSTORE_GW_SERVICE + '-' + process.env.OPENSHIFT_BUILD_NAMESPACE),
  SECURE_API_ENDPOINT: (process.env.SECURE_COOLSTORE_GW_ENDPOINT != null ? process.env.SECURE_COOLSTORE_GW_ENDPOINT  : process.env.SECURE_COOLSTORE_GW_SERVICE + '-' + process.env.SECURE_COOLSTORE_GW_SERVICE),
  SSO_ENABLED: process.env.SSO_URL ? true : false
};

module.exports = config;
