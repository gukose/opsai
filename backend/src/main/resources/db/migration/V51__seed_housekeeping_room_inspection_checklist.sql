-- Default MVP checklist for existing hotels; future templates remain extensible through InspectionTemplateService.
insert into housekeeping_checklist_template(id,hotel_id,workflow_type,name,active,created_at,updated_at,version_number,enabled)
select gen_random_uuid(),h.id,'DEPARTURE_CLEANING','Room Presentation Inspection',true,now(),now(),1,true
from hotel h
where not exists (
    select 1 from housekeeping_checklist_template t
    where t.hotel_id=h.id and t.workflow_type='DEPARTURE_CLEANING' and t.active=true and t.enabled=true
);

insert into housekeeping_checklist_item(id,template_id,code,label,required,display_order)
select gen_random_uuid(),t.id,v.code,v.label,true,v.ord
from housekeeping_checklist_template t
cross join (values
    ('BED_AND_LINEN','Bed and linen',1),
    ('BATHROOM','Bathroom',2),
    ('FLOOR_AND_SURFACES','Floor and surfaces',3),
    ('AMENITIES','Amenities',4),
    ('MINIBAR_AREA','Minibar area',5),
    ('TRASH_REMOVED','Trash removed',6),
    ('ROOM_PRESENTATION','Room presentation',7)
) v(code,label,ord)
where t.workflow_type='DEPARTURE_CLEANING'
  and t.name='Room Presentation Inspection'
  and not exists (select 1 from housekeeping_checklist_item i where i.template_id=t.id);
