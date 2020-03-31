INSERT
INTO   ORDER_PREPROCESSES
       (
              ID,
              BATCH_ID,
              EQUIP_CD,
              EQUIP_NM,
              SKU_QTY,
              TOTAL_PCS,
              DOMAIN_ID,
              SUB_EQUIP_CD,
              STATION_CD,
              CREATOR_ID,
              UPDATER_ID,
              CREATED_AT,
              UPDATED_AT
       )
SELECT F_GET_GENERATE_UUID(),
       X.BATCH_ID,
       X.EQUIP_CD,
       X.EQUIP_NM,
       X.SKU_QTY,
       X.TOTAL_PCS,
       X.DOMAIN_ID,
       Y.CELL_CD,
       Y.STATION_CD,
       :userId,
       :userId,
       SYSDATE,
       SYSDATE
FROM   ORDER_PREPROCESSES X,
       ( SELECT CELL_CD,
               STATION_CD
       FROM    CELLS
       WHERE   DOMAIN_ID = :domainId
       AND     EQUIP_CD  = :equipCd
       AND     SIDE_CD   = 'R'
       )
       Y
WHERE  X.EQUIP_CD              = :equipCd
AND    X.DOMAIN_ID             = :domainId
AND    X.BATCH_ID              = :batchId
AND    SUBSTR(X.EQUIP_CD , -2) = SUBSTR(Y.EQUIP_CD, -2)
