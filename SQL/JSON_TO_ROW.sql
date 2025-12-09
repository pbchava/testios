with json_encoded as (
    SELECT UTL_RAW.CAST_TO_VARCHAR2(UTL_ENCODE.BASE64_ENCODE(UTL_RAW.CAST_TO_RAW('{
    "customers": [
        {"id": 1, "name": "Alice", "city": "New York"},
        {"id": 2, "name": "Bob", "city": "London"}
    ]
    }'))) AS encoded_string
    FROM DUAL
),
json_info as (
    SELECT UTL_RAW.CAST_TO_VARCHAR2(UTL_ENCODE.BASE64_DECODE(UTL_RAW.CAST_TO_RAW(encoded_string))) AS json_document
    FROM json_encoded
)
SELECT
    jt.customer_id,
    jt.customer_name,
    jt.customer_city
FROM
    json_info,
    JSON_TABLE(
        json_info.json_document,
        '$.customers[*]'
        COLUMNS (
            customer_id    NUMBER PATH '$.id',
            customer_name  VARCHAR2(100) PATH '$.name',
            customer_city  VARCHAR2(100) PATH '$.city'
        )
    ) AS jt;