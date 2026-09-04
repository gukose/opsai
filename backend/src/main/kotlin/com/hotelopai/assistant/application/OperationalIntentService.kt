package com.hotelopai.assistant.application
enum class OperationalIntent { MAINTENANCE_REQUEST, GUEST_REQUEST, HOUSEKEEPING_REQUEST, MINIBAR_REPORT, DAMAGE_REPORT, SERVICE_RECOVERY, GENERAL_OPERATIONAL_NOTE, UNKNOWN }
data class OperationalEntities(val location:String?=null,val category:String?=null,val priority:String="MEDIUM",val requiredDepartment:String?=null,val requiredSkill:String?=null,val values:Map<String,String> = emptyMap())
data class OperationalIntentResult(val intent:OperationalIntent,val confidence:Double,val entities:OperationalEntities,val confirmationRequired:Boolean,val ruleVersion:String="intent-mvp-v1")
@org.springframework.stereotype.Service class OperationalIntentService {
 fun interpret(text:String,languageCode:String?=null):OperationalIntentResult {val n=text.trim().lowercase();require(n.length in 1..4000);val location=RoomNumberExtractor.extract(n)?.let{"Room $it"};val result=when{
  listOf("hvac","air condition","klima","leak","sızıntı","broken light","elektrik").any(n::contains)->OperationalIntent.MAINTENANCE_REQUEST to OperationalEntities(location,"TECHNICAL",if("urgent" in n||"acil" in n)"URGENT" else "HIGH","MAINTENANCE",if("hvac" in n||"air condition" in n||"klima" in n)"HVAC" else null)
  listOf("minibar","consumed","içildi","eksik ürün").any(n::contains)->OperationalIntent.MINIBAR_REPORT to OperationalEntities(location,"MINIBAR","HIGH","HOUSEKEEPING","MINIBAR")
  listOf("damage","damaged","hasar","kırık").any(n::contains)->OperationalIntent.DAMAGE_REPORT to OperationalEntities(location,"DAMAGE","HIGH","MAINTENANCE",null)
  listOf("clean","housekeeping","temizlik","dirty","pis","kirli").any(n::contains)->OperationalIntent.HOUSEKEEPING_REQUEST to OperationalEntities(location,"HOUSEKEEPING","MEDIUM","HOUSEKEEPING",null)
  listOf("complaint","unhappy","şikayet","service recovery").any(n::contains)->OperationalIntent.SERVICE_RECOVERY to OperationalEntities(location,"COMPLAINT","HIGH","FRONT_OFFICE",null)
  listOf("towel","water","pillow","havlu","su","yastık").any(n::contains)->OperationalIntent.GUEST_REQUEST to OperationalEntities(location,"GUEST_REQUEST","MEDIUM","HOUSEKEEPING",null)
  n.length>=20->OperationalIntent.GENERAL_OPERATIONAL_NOTE to OperationalEntities(location,"NOTE","LOW",null,null)
  else->OperationalIntent.UNKNOWN to OperationalEntities(location)
 };val confidence=if(result.first==OperationalIntent.UNKNOWN)0.25 else if(location!=null)0.92 else 0.72;return OperationalIntentResult(result.first,confidence,result.second.copy(values=languageCode?.let{mapOf("languageCode" to it)}?:emptyMap()),confidence<0.75) }
}
