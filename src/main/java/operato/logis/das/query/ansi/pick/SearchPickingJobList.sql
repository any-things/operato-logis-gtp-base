SELECT
    JOB.DOMAIN_ID,
    JOB.ID,
    JOB.BATCH_ID,
    JOB.STAGE_CD,
    JOB.COM_CD,
    JOB.JOB_TYPE,
    JOB.INPUT_SEQ,
    JOB.EQUIP_TYPE,
    JOB.EQUIP_CD,
    JOB.EQUIP_NM,
    JOB.SUB_EQUIP_CD,
    CELL.IND_CD,
    NVL(JOB.COLOR_CD, 'R') AS COLOR_CD,
    NVL(JOB.PICK_QTY, 0) AS PICK_QTY,
    NVL(JOB.PICKING_QTY, 0) AS PICKING_QTY,
    NVL(JOB.PICKED_QTY, 0) AS PICKED_QTY,
    JOB.BOX_IN_QTY,
    ROUTER.GW_NM AS GW_PATH,
    CELL.SIDE_CD,
    CELL.STATION_CD,
    JOB.ORDER_NO,
    JOB.SKU_CD,
    SKU.SKU_BARCD,
    JOB.SKU_NM,
    JOB.SHOP_CD,
    JOB.SHOP_NM,
    JOB.STATUS,
    JOB.BOX_ID,
    JOB.BOX_TYPE_CD,
    JOB.INVOICE_ID,
    JOB.BOX_PACK_ID,
    JOB.ORDER_TYPE,
    JOB.CLASS_CD,
    JOB.BOX_CLASS_CD,
    JOB.PICK_STARTED_AT,
    JOB.PICK_ENDED_AT
FROM
    JOB_INSTANCES JOB
    INNER JOIN CELLS CELL ON JOB.DOMAIN_ID = CELL.DOMAIN_ID AND JOB.EQUIP_TYPE = CELL.EQUIP_TYPE AND JOB.EQUIP_CD = CELL.EQUIP_CD AND JOB.SUB_EQUIP_CD = CELL.CELL_CD
    INNER JOIN SKU SKU ON JOB.COM_CD = SKU.COM_CD AND SKU.SKU_CD = JOB.SKU_CD
    LEFT OUTER JOIN INDICATORS IND ON CELL.DOMAIN_ID = IND.DOMAIN_ID AND CELL.IND_CD = IND.IND_CD
    LEFT OUTER JOIN GATEWAYS ROUTER ON IND.DOMAIN_ID = ROUTER.DOMAIN_ID AND IND.GW_CD = ROUTER.GW_CD
WHERE
    JOB.DOMAIN_ID = :domainId
    #if($batchId)
    AND JOB.BATCH_ID = :batchId
    #end
    #if($inputSeq)
    AND JOB.INPUT_SEQ = :inputSeq
    #end
    #if($comCd)
    AND JOB.COM_CD = :comCd
    #end
    #if($status)
    AND JOB.STATUS = :status
    #end
    #if($statuses)
    AND JOB.STATUS IN (:statuses)
    #end
    #if($classCd)
    AND JOB.CLASS_CD = :classCd
    #end
    #if($boxClassCd)
    AND JOB.BOX_CLASS_CD = :boxClassCd
    #end
    #if($boxId)
    AND JOB.BOX_ID = :boxId
    #end
    #if($skuCd)
    AND JOB.SKU_CD = :skuCd
    #end
    #if($equipType)
    AND CELL.EQUIP_TYPE = :equipType
    #end
    #if($equipCd)
    AND CELL.EQUIP_CD = :equipCd
    #end
    #if($stationCd)
    AND CELL.STATION_CD = :stationCd
    #end
    #if($equipZoneCd)
    AND CELL.EQUIP_ZONE_CD = :equipZoneCd
    #end
    #if($sideCd)
    AND CELL.SIDE_CD = :sideCd
    #end
    #if($indCd)
    AND CELL.IND_CD = :indCd
    #end
    #if($gwCd)
    AND ROUTER.GW_CD = :gwCd
    #end
    #if($pickingQty)
    AND JOB.PICKING_QTY >= :pickingQty
    #end
    #if($onlyOne)
    AND ROWNUM = 1
    #end		   
ORDER BY
    ROUTER.GW_NM ASC, CELL.STATION_CD ASC, JOB.SUB_EQUIP_CD ASC
