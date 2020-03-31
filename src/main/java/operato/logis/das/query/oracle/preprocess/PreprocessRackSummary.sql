SELECT   BATCH_ID,
         EQUIP_CD,
         EQUIP_NM
FROM     ( SELECT  BATCH_ID,
                  CASE
                           WHEN EQUIP_CD IS NULL OR EQUIP_CD = ''
                           THEN 100000
                           ELSE RANK() OVER ( ORDER BY EQUIP_CD )
                  END SORT_COL,
                  EQUIP_CD,
                  EQUIP_NM
         FROM     ORDER_PREPROCESSES
         WHERE    BATCH_ID = :batchId
         GROUP BY BATCH_ID,
                  EQUIP_CD,
                  EQUIP_NM
         ) A
ORDER BY SORT_COL ASC
