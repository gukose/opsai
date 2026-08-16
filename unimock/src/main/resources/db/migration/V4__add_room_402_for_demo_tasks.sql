update unimock.simulation_document
set payload_json = jsonb_set(
        payload_json,
        '{floors}',
        coalesce(payload_json -> 'floors', '[]'::jsonb) || jsonb_build_array(
            jsonb_build_object('code', 'F4', 'name', 'Fourth Floor')
        )
    ),
    version = version + 1,
    updated_at = now()
where document_path = 'master/floors.json'
  and not coalesce(payload_json -> 'floors', '[]'::jsonb) @> '[{"code":"F4"}]'::jsonb;

update unimock.simulation_document
set payload_json = jsonb_set(
        payload_json,
        '{rooms}',
        coalesce(payload_json -> 'rooms', '[]'::jsonb) || jsonb_build_array(
            jsonb_build_object(
                'roomNumber', '402',
                'roomTypeCode', 'DELUXE',
                'floorCode', 'F4',
                'status', 'OCCUPIED',
                'isOutOfOrder', false
            )
        )
    ),
    version = version + 1,
    updated_at = now()
where document_path = 'master/rooms.json'
  and not coalesce(payload_json -> 'rooms', '[]'::jsonb) @> '[{"roomNumber":"402"}]'::jsonb;
