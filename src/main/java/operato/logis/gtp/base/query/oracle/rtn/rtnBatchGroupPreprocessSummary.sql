SELECT NVL(TOTAL_SKUS, 0)                      AS TOTAL_SKUS ,
       NVL(ASSIGNED_SKU, 0)                    AS ASSIGNED_SKU ,
       NVL(TOTAL_SKUS - ASSIGNED_SKU, 0)       AS REMAIN_SKU ,
       NVL(TOTAL_ORDERS_PCS, 0)                AS TOTAL_ORDERS_PCS ,
       NVL(ASSIGNED_PCS, 0)                    AS ASSIGNED_PCS ,
       NVL(TOTAL_ORDERS_PCS - ASSIGNED_PCS, 0) AS REMAIN_PCS
FROM   ( SELECT COUNT(1)                  AS TOTAL_SKUS ,
               SUM(ASSIGNED_SKU    / SKU_CNT) AS ASSIGNED_SKU ,
               SUM(TOTAL_ORDERS_PCS/ SKU_CNT) AS TOTAL_ORDERS_PCS ,
               SUM(ASSIGNED_PCS    / SKU_CNT) AS ASSIGNED_PCS
       FROM    ( SELECT  CELL_ASSGN_CD SKU_CD ,
                        COUNT(1) AS SKU_CNT ,
                        SUM(
                        CASE
                                 WHEN EQUIP_CD IS NULL OR EQUIP_CD = ''
                                 THEN 0
                                 ELSE 1
                        END)           AS ASSIGNED_SKU ,
                        SUM(TOTAL_PCS) AS TOTAL_ORDERS_PCS ,
                        SUM(
                        CASE
                                 WHEN EQUIP_CD IS NULL OR EQUIP_CD = ''
                                 THEN 0
                                 ELSE TOTAL_PCS
                        END) AS ASSIGNED_PCS
               FROM     ORDER_PREPROCESSES
               WHERE    DOMAIN_ID = :domainId
               AND      BATCH_ID  = :batchId #if($classCd)
               AND
                        (
                                 'ALL' = :orderGroup OR CLASS_CD = :classCd
                        )
                        #else
               AND
                        (
                                 CLASS_CD IS NULL OR CLASS_CD = ''
                        )
                        #end #if($rackCd)
               AND
                        (
                                 'ALL' = :rackCd OR EQUIP_CD = :rackCd
                        )
                        #else
               AND
                        (
                                 EQUIP_CD IS NULL OR EQUIP_CD = ''
                        )
                        #end
               GROUP BY CELL_ASSGN_CD
               )
       )
