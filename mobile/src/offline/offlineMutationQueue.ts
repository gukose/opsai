import type { OfflineScope } from "./offlineTypes";

export type OfflineMutationType = "TASK_START" | "TASK_PAUSE" | "TASK_RESUME" | "TASK_COMPLETE";
export type OfflineMutationState = "PENDING" | "SYNCING" | "CONFLICT";
export type OfflineMutation = { clientOperationId:string; type:OfflineMutationType; resourceId:string; createdAt:string; state:OfflineMutationState; lastError?:string|null };
const memory=new Map<string,string>();

export class OfflineMutationQueue {
 async list(scope:OfflineScope):Promise<OfflineMutation[]>{const raw=await get(key(scope));if(!raw)return [];try{const parsed=JSON.parse(raw);return Array.isArray(parsed)?parsed.filter(valid):[]}catch{return []}}
 async enqueue(scope:OfflineScope,type:OfflineMutationType,resourceId:string,now:Date=new Date()):Promise<OfflineMutation>{const items=await this.list(scope);const existing=items.find(x=>x.type===type&&x.resourceId===resourceId&&x.state!=="CONFLICT");if(existing)return existing;const mutation={clientOperationId:`${now.getTime()}-${randomId()}`,type,resourceId,createdAt:now.toISOString(),state:"PENDING" as const};await set(key(scope),JSON.stringify([...items,mutation]));return mutation}
 async sync(scope:OfflineScope,submit:(item:OfflineMutation)=>Promise<void>):Promise<{synced:number;conflicts:number;pending:number}>{const items=await this.list(scope);const remaining:OfflineMutation[]=[];let synced=0,conflicts=0;for(const item of items){try{await submit({...item,state:"SYNCING"});synced++}catch(error){const status=typeof error==="object"&&error&&"status" in error?Number((error as {status?:number}).status):undefined;if(status&&status>=400&&status<500){remaining.push({...item,state:"CONFLICT",lastError:"Server rejected this operation; supervisor review is required."});conflicts++}else{remaining.push({...item,state:"PENDING",lastError:"Network unavailable; retry is pending."})}}}await set(key(scope),JSON.stringify(remaining));return{synced,conflicts,pending:remaining.filter(x=>x.state==="PENDING").length}}
 async clear(scope:OfflineScope){await remove(key(scope))}
}
function key(s:OfflineScope){return `hotel-opai.offline:v1:${s.hotelId}:${s.userId}:mutation-queue:core`}
function valid(v:unknown):v is OfflineMutation{return Boolean(v&&typeof v==="object"&&"clientOperationId" in v&&"type" in v&&"resourceId" in v)}
function randomId(){return Math.random().toString(36).slice(2,10)}
async function get(k:string){return typeof globalThis.localStorage!=="undefined"?globalThis.localStorage.getItem(k):memory.get(k)??null}
async function set(k:string,v:string){if(typeof globalThis.localStorage!=="undefined")globalThis.localStorage.setItem(k,v);else memory.set(k,v)}
async function remove(k:string){if(typeof globalThis.localStorage!=="undefined")globalThis.localStorage.removeItem(k);else memory.delete(k)}
export const defaultOfflineMutationQueue=new OfflineMutationQueue();
