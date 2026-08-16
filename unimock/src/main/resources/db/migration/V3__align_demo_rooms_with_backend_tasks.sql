update unimock.simulation_document
set payload_json = jsonb_set(
        payload_json,
        '{rooms}',
        coalesce(payload_json -> 'rooms', '[]'::jsonb) || jsonb_build_array(
            jsonb_build_object(
                'roomNumber', '204',
                'roomTypeCode', 'DELUXE',
                'floorCode', 'F2',
                'status', 'OCCUPIED',
                'isOutOfOrder', false
            )
        )
    ),
    version = version + 1,
    updated_at = now()
where document_path = 'master/rooms.json'
  and not coalesce(payload_json -> 'rooms', '[]'::jsonb) @> '[{"roomNumber":"204"}]'::jsonb;

update unimock.simulation_document
set payload_json = jsonb_set(
        payload_json,
        '{rooms}',
        coalesce(payload_json -> 'rooms', '[]'::jsonb) || jsonb_build_array(
            jsonb_build_object(
                'roomNumber', '302',
                'roomTypeCode', 'DELUXE',
                'floorCode', 'F3',
                'status', 'OCCUPIED',
                'isOutOfOrder', false
            )
        )
    ),
    version = version + 1,
    updated_at = now()
where document_path = 'master/rooms.json'
  and not coalesce(payload_json -> 'rooms', '[]'::jsonb) @> '[{"roomNumber":"302"}]'::jsonb;

update unimock.simulation_document
set payload_json = jsonb_set(
        payload_json,
        '{floors}',
        coalesce(payload_json -> 'floors', '[]'::jsonb) || jsonb_build_array(
            jsonb_build_object('code', 'F3', 'name', 'Third Floor')
        )
    ),
    version = version + 1,
    updated_at = now()
where document_path = 'master/floors.json'
  and not coalesce(payload_json -> 'floors', '[]'::jsonb) @> '[{"code":"F3"}]'::jsonb;
