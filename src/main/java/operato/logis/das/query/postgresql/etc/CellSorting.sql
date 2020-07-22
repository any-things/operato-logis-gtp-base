SELECT   *
FROM     CELLS X
WHERE    DOMAIN_ID = :domainId
AND      EQUIP_CD IN (:equipCds)
AND      ACTIVE_FLAG = true
AND
         (
                  SIDE_CD =
                  (SELECT
                          CASE
                                  WHEN RACK_TYPE = 'P'
                                  THEN 'F'
                                  ELSE 'R'
                          END
                  FROM    RACKS
                  WHERE   DOMAIN_ID = X.DOMAIN_ID
                  AND     RACK_CD = X.EQUIP_CD
                  )
         OR SIDE_CD = 'F'
         )
ORDER BY EQUIP_CD,
         CELL_SEQ,
         SIDE_CD
