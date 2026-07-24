import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(packageRoot, "../..");
const specPath = path.join(repoRoot, "docs/api/openapi-v1.yaml");
const HTTP_METHODS = ["get", "put", "post", "delete", "patch", "head", "options", "trace"];
const outputArg = process.argv.find((arg) => arg.startsWith("--out="));
const generatedDir = outputArg
  ? path.resolve(outputArg.slice("--out=".length))
  : path.join(packageRoot, "src/generated");

mkdirSync(generatedDir, { recursive: true });

const schemaOutput = path.join(generatedDir, "schema.ts");
const openapiTypescript = path.join(packageRoot, "node_modules/.bin/openapi-typescript");
const generation = spawnSync(openapiTypescript, [specPath, "-o", schemaOutput], { cwd: packageRoot, stdio: "inherit" });
if (generation.status !== 0) {
  throw new Error("openapi-typescript generation failed");
}

const spec = YAML.parse(readFileSync(specPath, "utf8"));
const operations = collectOperations(spec);
const operationIds = operations.map((operation) => operation.operationId);
const duplicates = operationIds.filter((id, index) => operationIds.indexOf(id) !== index);
if (duplicates.length > 0) {
  throw new Error(`Duplicate operation IDs: ${[...new Set(duplicates)].sort().join(", ")}`);
}

const functionNames = operations.map((operation) => operation.functionName);
const functionDuplicates = functionNames.filter((id, index) => functionNames.indexOf(id) !== index);
if (functionDuplicates.length > 0) {
  throw new Error(`Generated TypeScript operation name collision: ${[...new Set(functionDuplicates)].sort().join(", ")}`);
}

writeFileSync(path.join(generatedDir, "operations.ts"), renderOperations(operations));

function collectOperations(openapi) {
  const result = [];
  const globalSecurity = Array.isArray(openapi.security) && openapi.security.length > 0;
  for (const routePath of Object.keys(openapi.paths ?? {}).sort()) {
    if (!routePath.startsWith("/api/v1/")) {
      throw new Error(`Public SDK source contains non-v1 path: ${routePath}`);
    }
    if (routePath.startsWith("/api/v1/dev/pms")) {
      throw new Error(`Public SDK source contains development-only route: ${routePath}`);
    }
    const pathItem = openapi.paths[routePath];
    for (const method of Object.keys(pathItem).filter((name) => HTTP_METHODS.includes(name)).sort()) {
      const operation = pathItem[method];
      if (!operation.operationId) {
        throw new Error(`Missing operationId for ${method.toUpperCase()} ${routePath}`);
      }
      const functionName = toIdentifier(operation.operationId);
      const requestBody = firstRequestBody(operation);
      const response = firstSuccessResponse(operation);
      result.push({
        routePath,
        method: method.toUpperCase(),
        operationId: operation.operationId,
        functionName,
        auth: operation.security === undefined ? globalSecurity : Array.isArray(operation.security) && operation.security.length > 0,
        hasBody: Boolean(requestBody),
        bodyRequired: Boolean(operation.requestBody?.required),
        hasPathParams: operation.parameters?.some((parameter) => parameter.in === "path") ?? false,
        hasQueryParams: operation.parameters?.some((parameter) => parameter.in === "query") ?? false,
        successStatus: response.status,
        successContentType: response.contentType
      });
    }
  }
  return result;
}

function renderOperations(operations) {
  const lines = [
    "/* Auto-generated from docs/api/openapi-v1.yaml. Do not edit manually. */",
    "import type { ApiResponse, HotelOpAiClient } from \"../client.js\";",
    "import type { paths } from \"./schema.js\";",
    "",
    "type HttpMethodKey = \"get\" | \"put\" | \"post\" | \"delete\" | \"patch\" | \"head\" | \"options\" | \"trace\";",
    "type Operation<Path extends keyof paths, Method extends HttpMethodKey> = Method extends keyof paths[Path] ? paths[Path][Method] : never;",
    "type JsonContent<Content> = Content extends { \"application/json\": infer Json } ? Json : Content extends { \"*/*\": infer Any } ? Any : unknown;",
    "type RequestBody<Op> = Op extends { requestBody: { content: infer Content } } ? JsonContent<Content> : never;",
    "type PathParams<Op> = Op extends { parameters: { path: infer Params } } ? Params : never;",
    "type QueryParams<Op> = Op extends { parameters: { query?: infer Params } } ? Params : never;",
    "type SuccessResponse<Responses> = 200 extends keyof Responses ? Responses[200] : 201 extends keyof Responses ? Responses[201] : 202 extends keyof Responses ? Responses[202] : 204 extends keyof Responses ? Responses[204] : never;",
    "type ResponseBody<Op> = Op extends { responses: infer Responses } ? SuccessResponse<Responses> extends { content: infer Content } ? JsonContent<Content> : undefined : never;",
    ""
  ];

  for (const operation of operations) {
    const methodKey = operation.method.toLowerCase();
    const typePrefix = operation.functionName;
    const opType = `Operation<\"${operation.routePath}\", \"${methodKey}\">`;
    lines.push(`type ${typePrefix}Operation = ${opType};`);
    lines.push(`export type ${typePrefix}Response = ResponseBody<${typePrefix}Operation>;`);
    if (operation.hasBody) lines.push(`export type ${typePrefix}Request = RequestBody<${typePrefix}Operation>;`);
    if (operation.hasPathParams) lines.push(`export type ${typePrefix}PathParams = PathParams<${typePrefix}Operation>;`);
    if (operation.hasQueryParams) lines.push(`export type ${typePrefix}QueryParams = QueryParams<${typePrefix}Operation>;`);
    lines.push(`export type ${typePrefix}Options = {`);
    if (operation.hasPathParams) lines.push(`  readonly pathParams: ${typePrefix}PathParams;`);
    if (operation.hasQueryParams) lines.push(`  readonly query?: ${typePrefix}QueryParams;`);
    if (operation.hasBody) {
      const optional = operation.bodyRequired ? "" : "?";
      lines.push(`  readonly body${optional}: ${typePrefix}Request;`);
    }
    lines.push("  readonly signal?: AbortSignal;");
    lines.push("};");
    const optionalOptions = operation.hasPathParams || operation.bodyRequired ? "" : " = {}";
    lines.push(`export function ${operation.functionName}(client: HotelOpAiClient, options: ${typePrefix}Options${optionalOptions}): Promise<ApiResponse<${typePrefix}Response>> {`);
    lines.push(`  return client.request<${typePrefix}Response, ${operation.hasBody ? `${typePrefix}Request` : "undefined"}, ${operation.hasQueryParams ? `${typePrefix}QueryParams` : "undefined"}, ${operation.hasPathParams ? `${typePrefix}PathParams` : "undefined"}>({`);
    lines.push(`    method: "${operation.method}",`);
    lines.push(`    path: "${operation.routePath}",`);
    lines.push(`    auth: ${operation.auth},`);
    if (operation.hasPathParams) lines.push("    pathParams: options.pathParams,");
    if (operation.hasQueryParams) lines.push("    ...(options.query !== undefined ? { query: options.query } : {}),");
    if (operation.hasBody) {
      if (operation.bodyRequired) {
        lines.push("    body: options.body,");
      } else {
        lines.push("    ...(options.body !== undefined ? { body: options.body } : {}),");
      }
    }
    lines.push("    ...(options.signal !== undefined ? { signal: options.signal } : {})");
    lines.push("  });");
    lines.push("}");
    lines.push("");
  }
  return `${lines.join("\n").trimEnd()}\n`;
}

function firstRequestBody(operation) {
  const content = operation.requestBody?.content;
  if (!content) return null;
  const media = content["application/json"] ?? content["*/*"] ?? Object.values(content)[0] ?? null;
  return media?.schema ?? null;
}

function firstSuccessResponse(operation) {
  for (const status of Object.keys(operation.responses ?? {}).sort()) {
    if (!status.startsWith("2")) continue;
    const response = operation.responses[status];
    const content = response.content ?? {};
    const contentType = content["application/json"] ? "application/json" : content["*/*"] ? "*/*" : Object.keys(content).sort()[0] ?? null;
    return { status, contentType };
  }
  throw new Error(`Operation ${operation.operationId} has no 2xx response`);
}

function toIdentifier(operationId) {
  if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(operationId)) {
    throw new Error(`Operation ID is not a valid TypeScript identifier: ${operationId}`);
  }
  return operationId;
}
