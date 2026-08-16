const path = require("node:path");
const { getDefaultConfig } = require("expo/metro-config");

const projectRoot = __dirname;
const repositoryRoot = path.resolve(projectRoot, "..");
const apiClientRoot = path.resolve(repositoryRoot, "sdk/typescript");

const config = getDefaultConfig(projectRoot);

config.watchFolders = [repositoryRoot];
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(repositoryRoot, "node_modules"),
];
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  "@hotelopai/api-client": apiClientRoot,
};

module.exports = config;
