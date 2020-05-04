SELECT TOTAL_SKUS,
       ASSIGNED_SKU,
       (TOTAL_SKUS - ASSIGNED_SKU) AS REMAIN_SKU,
       TOTAL_ORDERS_PCS,
       ASSIGNED_PCS,
       (TOTAL_ORDERS_PCS - ASSIGNED_PCS) AS REMAIN_PCS
FROM   ( 
   SELECT A.TOTAL_SKUS,
           COALESCE(B.ASSIGNED_SKU, 0) AS ASSIGNED_SKU,
           A.TOTAL_ORDERS_PCS,
           COALESCE(B.ASSIGNED_PCS, 0) AS ASSIGNED_PCS
   FROM    ( SELECT  BATCH_ID,
                    COUNT(DISTINCT(SKU_CD)) AS TOTAL_SKUS,
                    SUM(ORDER_QTY)          AS TOTAL_ORDERS_PCS
           FROM     ORDERS
           WHERE    BATCH_ID = :batchId
           GROUP BY BATCH_ID
           )
           A
           LEFT OUTER JOIN
                   (SELECT  BATCH_ID,
                            SUM(ASSIGNED_SKU)         AS ASSIGNED_SKU,
                            SUM(ASSIGNED_PCS/SKU_CNT) AS ASSIGNED_PCS
                   FROM     (SELECT  BATCH_ID,
                                     COUNT(1)       AS SKU_CNT ,
                                     1              AS ASSIGNED_SKU,
                                     SUM(TOTAL_PCS) AS ASSIGNED_PCS
                            FROM     ORDER_PREPROCESSES
                            WHERE    BATCH_ID = :batchId
                            AND
                                     (
                                              EQUIP_CD IS NOT NULL
                                     AND      LENGTH(EQUIP_CD)   > 0
                                     )
                            GROUP BY BATCH_ID,
                                     CELL_ASSGN_CD
                            )
                   GROUP BY BATCH_ID
                   )
                   B
           ON      A.BATCH_ID = B.BATCH_ID
)
