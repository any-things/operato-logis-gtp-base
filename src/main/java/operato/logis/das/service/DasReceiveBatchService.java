package operato.logis.das.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import operato.logis.das.query.store.DasQueryStore;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BatchReceipt;
import xyz.anythings.base.entity.BatchReceiptItem;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.event.main.BatchReceiveEvent;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.dbist.util.StringJoiner;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.util.ValueUtil;

/**
 * DAS, 반품 주문 수신용 서비스
 * 
 * @author shortstop
 */
@Component
public class DasReceiveBatchService extends AbstractQueryService {
	
	/**
	 * 반품 관련 쿼리 스토어 
	 */
	@Autowired
	private DasQueryStore batchQueryStore;

	/**
	 * 주문 정보 수신을 위한 수신 서머리 정보 조회
	 *  
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 10 and #event.eventStep == 1 and (#event.jobType == 'DAS' or #event.jobType == 'RTN')")
	public void handleReadyToReceive(BatchReceiveEvent event) { 
		BatchReceipt receipt = event.getReceiptData();
		String jobType = event.getJobType();
		
		receipt = this.createReadyToReceiveData(receipt,jobType);
		event.setReceiptData(receipt);
	}
	
	/**
	 * 배치 수신 서머리 데이터 생성 
	 * 
	 * @param receipt
	 * @param jobType
	 * @param params
	 * @return
	 */
	private BatchReceipt createReadyToReceiveData(BatchReceipt receipt, String jobType, Object ... params) {
		// 1. 대기 상태 이거나 진행 중인 수신이 있는지 확인
		
		BatchReceipt runBatchReceipt = this.checkRunningOrderReceipt(receipt,jobType);
		if(runBatchReceipt != null) return runBatchReceipt;
		
		// 2. WMS IF 테이블에서 수신 대상 데이터 확인
		List<BatchReceiptItem> receiptItems = this.getWmfIfToReceiptItems(receipt);
		
		// 3 수신 아이템 데이터 생성 
		for(BatchReceiptItem item : receiptItems) {
			item.setBatchId(LogisBaseUtil.newReceiptJobBatchId(receipt.getDomainId()));
			item.setBatchReceiptId(receipt.getId());
			this.queryManager.insert(item);
		}
		
		// 4. 수신 아이템 설정 및 리턴
		receipt.setItems(receiptItems);
		return receipt;
	}
	
	/**
	 * WMS IF 테이블에서 수신 대상 데이터 확인
	 * 
	 * @param receipt
	 * @return
	 */
	private List<BatchReceiptItem> getWmfIfToReceiptItems(BatchReceipt receipt) {
		Map<String,Object> params = ValueUtil.newMap("domainId,comCd,areaCd,stageCd,jobDate",
				receipt.getDomainId(), receipt.getComCd(), receipt.getAreaCd(), receipt.getStageCd(), receipt.getJobDate());
		
		return this.queryManager.selectListBySql(this.batchQueryStore.getWmsIfToReceiptDataQuery(), params, BatchReceiptItem.class, 0, 0);
	}
	
	/**
	 * 대기 상태 이거나 진행 중인 수신이 있는지 확인
	 * 
	 * @param domainId
	 * @param jobType
	 * @return
	 */
	private BatchReceipt checkRunningOrderReceipt(BatchReceipt receipt, String jobType) {
		Map<String,Object> paramMap = ValueUtil.newMap("domainId,comCd,areaCd,stageCd,jobDate,status,jobType", 
				receipt.getDomainId(), receipt.getComCd(), receipt.getAreaCd(), receipt.getStageCd(), receipt.getJobDate(),
				ValueUtil.newStringList(LogisConstants.COMMON_STATUS_WAIT, LogisConstants.COMMON_STATUS_RUNNING), jobType);
		 
		BatchReceipt receiptData = 
				this.queryManager.selectBySql(this.batchQueryStore.getBatchReceiptOrderTypeStatusQuery(), paramMap, BatchReceipt.class);
		
		// 대기 중 또는 진행 중인 수신 정보 리턴 
		if(receiptData != null) {
			receiptData.setItems(AnyEntityUtil.searchDetails(receipt.getDomainId(), BatchReceiptItem.class, "batchReceiptId", receiptData.getId()));
			return receiptData;
		}
		
		return null;
	}
	
	/**
	 * 주문 정보 수신 시작
	 * 
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 20 and #event.eventStep == 1 and (#event.jobType == 'DAS' or #event.jobType == 'RTN')")
	public void handleStartToReceive(BatchReceiveEvent event) {
		BatchReceipt receipt = event.getReceiptData();
		List<BatchReceiptItem> items = receipt.getItems();
		 
		for(BatchReceiptItem item : items) {
			if(ValueUtil.isEqualIgnoreCase(LogisConstants.JOB_TYPE_DAS, item.getJobType()) || ValueUtil.isEqualIgnoreCase(LogisConstants.JOB_TYPE_RTN, item.getJobType())) {
				this.startToReceiveData(receipt, item);
			}
		}
		 
	}
	
	/*@Async
	public void asyncStartToReceive(BatchReceipt receipt, BatchReceiptItem item) {
		Domain domain = Domain.find(receipt.getDomainId());
		DomainContext.setCurrentDomain(domain);
		
		try {
			this.startToReceiveData(receipt, item);
		} finally {
			DomainContext.unsetAll();
		}
	}*/
	
	/**
	 * 배치, 작업 수신
	 * 
	 * @param receipt
	 * @param item
	 * @param params
	 * @return
	 */
	private BatchReceipt startToReceiveData(BatchReceipt receipt, BatchReceiptItem item, Object ... params) {		
		// 1. TODO : 데이터 복사 방식 / 컬럼 설정에서 가져오기 
		String[] sourceFields = {"WMS_BATCH_NO", "WCS_BATCH_NO", "JOB_DATE", "JOB_SEQ", "JOB_TYPE", "ORDER_DATE", "ORDER_NO", "ORDER_LINE_NO", "ORDER_DETAIL_ID", "CUST_ORDER_NO", "CUST_ORDER_LINE_NO", "COM_CD", "AREA_CD", "STAGE_CD", "EQUIP_TYPE", "EQUIP_CD", "EQUIP_NM", "SUB_EQUIP_CD", "SHOP_CD", "SHOP_NM", "SKU_CD", "SKU_BARCD", "SKU_NM", "BOX_TYPE_CD", "BOX_IN_QTY", "ORDER_QTY", "PICKED_QTY", "BOXED_QTY", "CANCEL_QTY", "BOX_ID", "INVOICE_ID", "ORDER_TYPE", "CLASS_CD", "PACK_TYPE", "VEHICLE_NO", "LOT_NO", "FROM_ZONE_CD", "FROM_CELL_CD", "TO_ZONE_CD", "TO_CELL_CD"};
		String[] targetFields = {"WMS_BATCH_NO", "WCS_BATCH_NO", "JOB_DATE", "JOB_SEQ", "JOB_TYPE", "ORDER_DATE", "ORDER_NO", "ORDER_LINE_NO", "ORDER_DETAIL_ID", "CUST_ORDER_NO", "CUST_ORDER_LINE_NO", "COM_CD", "AREA_CD", "STAGE_CD", "EQUIP_TYPE", "EQUIP_CD", "EQUIP_NM", "SUB_EQUIP_CD", "SHOP_CD", "SHOP_NM", "SKU_CD", "SKU_BARCD", "SKU_NM", "BOX_TYPE_CD", "BOX_IN_QTY", "ORDER_QTY", "PICKED_QTY", "BOXED_QTY", "CANCEL_QTY", "BOX_ID", "INVOICE_ID", "ORDER_TYPE", "CLASS_CD", "PACK_TYPE", "VEHICLE_NO", "LOT_NO", "FROM_ZONE_CD", "FROM_CELL_CD", "TO_ZONE_CD", "TO_CELL_CD"};
		String fieldNames = "COM_CD,AREA_CD,STAGE_CD,WMS_BATCH_NO,IF_FLAG";
		int jobSeq = JobBatch.getMaxJobSeq(receipt.getDomainId(), receipt.getComCd(), receipt.getAreaCd(), receipt.getAreaCd(), receipt.getJobDate());		
		boolean exceptionOccurred = false;
		
		try {
			// 2. skip 이면 pass
			if(item.getSkipFlag()) {
				item.updateStatusImmediately(LogisConstants.COMMON_STATUS_SKIPPED, null);
				return receipt;
			}
						
			// 3. BatchReceiptItem 상태 업데이트  - 진행 중 
			item.updateStatusImmediately(LogisConstants.COMMON_STATUS_RUNNING, null);
			
			// 4. JobBatch 생성 
			JobBatch batch = JobBatch.createJobBatch(item.getBatchId(), item.getJobSeq(), receipt, item);
			
			// 5. 데이터 복사  
			this.cloneData(item.getBatchId(), jobSeq, "wms_if_orders", sourceFields, targetFields, fieldNames, item.getComCd(), item.getAreaCd(), item.getStageCd(), item.getWmsBatchNo(), LogisConstants.N_CAP_STRING);
			
			// 6. 셀과 매핑될 필드명을 스테이지 별 설정에서 조회 
			/*String classCd = StageJobConfigUtil.getCellMappingTargetField(item.getStageCd(), item.getJobType());
			String sql = "update orders set class_cd = :classCd where domain_id = :domainId and batch_id = :batchId";
			this.queryManager.executeBySql(sql, ValueUtil.newMap("domainId,batchId,classCd", item.getDomainId(), item.getBatchId(), classCd));*/
			
			// 7. JobBatch 상태 변경  
			batch.updateStatusImmediately(LogisConstants.isB2CJobType(batch.getJobType())? JobBatch.STATUS_READY : JobBatch.STATUS_WAIT);
			
			// 8. batchReceiptItem 상태 업데이트 
			item.updateStatusImmediately(LogisConstants.COMMON_STATUS_FINISHED, null);
			
			// 9.Wms_if_order 상태 업데이트
			this.updateWmfIfToReceiptItems(item,receipt.getJobDate());
			
		} catch(Exception e) {
			exceptionOccurred = true;
			String errMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
			errMsg = errMsg.length() > 400 ? errMsg.substring(0,400) : errMsg;
			item.updateStatusImmediately(LogisConstants.COMMON_STATUS_ERROR, errMsg);
		}
		
		// 10. 에러 발생인 경우 수신 상태 에러로 업데이트
		if(exceptionOccurred) {
			receipt.updateStatusImmediately(LogisConstants.COMMON_STATUS_ERROR);
		}
		
		return receipt;
	}
	
	/**
	 * 데이터 복제
	 * 
	 * @param sourceTable
	 * @param targetTable
	 * @param sourceFields
	 * @param targetFields
	 * @param fieldNames
	 * @param fieldValues
	 * @return
	 */
	@Transactional(propagation=Propagation.REQUIRES_NEW) 
	private void cloneData(String batchId, int jobSeq
								, String sourceTable
								, String[] sourceFields, String[] targetFields
								, String fieldNames, Object ... fieldValues) throws Exception {
		
		// 1. 조회 쿼리 생성  
		StringJoiner qry = new StringJoiner(SysConstants.LINE_SEPARATOR);
		
		// 1.1 select 필드 셋팅 
		qry.add("select 1 ");
		for(int i = 0 ; i < sourceFields.length ;i++) {
			qry.add(" , " + sourceFields[i] + " as " + targetFields[i]);
		}
		
		// 1.2 테이블 
		qry.add("  from " + sourceTable);
		
		// 1.3 where 조건 생성 
		StringJoiner whereStr = new StringJoiner(SysConstants.LINE_SEPARATOR);
		String[] keyArr = fieldNames.split(SysConstants.COMMA);
		
		
		// 1.3.1 치환 가능 하도록 쿼리문 생성 
		whereStr.add("where 1 = 1 ");
		for(String key : keyArr) {
			whereStr.add(" and " + key + " = :" + key);
		}
		qry.add(whereStr.toString());
		
		// 2. 조회  
		Map<String,Object> params = ValueUtil.newMap(fieldNames, fieldValues);
		List<Order> sourceList = this.queryManager.selectListBySql(qry.toString(), params, Order.class, 0, 0);

		List<Order> targetList = new ArrayList<Order>(sourceList.size());
		// 3. target 데이터 생성 
		for(Order sourceItem : sourceList) {
			Order targetItem = AnyValueUtil.populate(sourceItem, new Order());
			
			targetItem.setBatchId(batchId);
			targetItem.setJobSeq(jobSeq);
			targetItem.setStatus(Order.STATUS_WAIT);
			targetList.add(targetItem);
		}
		
		// 4. 데이터 insert 
		this.queryManager.insertBatch(targetList);
	}
	
	/**
	 * 주문 수신 취소
	 * 
	 * @param event
	 */
	@EventListener(classes = BatchReceiveEvent.class, condition = "#event.eventType == 30 and #event.eventStep == 1 and (#event.jobType == 'DAS' or #event.jobType == 'RTN')")
	public void handleCancelReceived(BatchReceiveEvent event) {
		// 1. 작업 배치 추출 
		JobBatch batch = event.getJobBatch();
		
		// 2. 배치 상태 체크
		String sql = "select status from job_batches where domain_id = :domainId and id = :id";
		Map<String, Object> params = ValueUtil.newMap("domainId,id", batch.getDomainId(), batch.getId());
		String currentStatus = AnyEntityUtil.findItem(batch.getDomainId(), true, String.class, sql, params);
		
		if(ValueUtil.isNotEqual(currentStatus, JobBatch.STATUS_WAIT) && ValueUtil.isNotEqual(currentStatus, JobBatch.STATUS_READY)) {
			throw new ElidomRuntimeException("작업 대기 상태에서만 취소가 가능 합니다.");
		}
		
		// 3. 주문 취소시 데이터 유지 여부에 따라서
		boolean isKeepData = BatchJobConfigUtil.isDeleteWhenOrderCancel(batch);
		int cancelledCnt = isKeepData ? this.cancelOrderKeepData(batch) : this.cancelOrderDeleteData(batch);
		event.setResult(cancelledCnt);
	}
	
	/**
	 * 주문 데이터 삭제 update
	 * 
	 * seq = 0
	 * @param batch
	 * @return
	 */
	private int cancelOrderKeepData(JobBatch batch) {
		int cnt = 0;
		
		// 1. 배치 상태  update 
		batch.updateStatus(JobBatch.STATUS_CANCEL);
		
		// 2. 주문 조회 
		List<Order> orderList = AnyEntityUtil.searchEntitiesBy(batch.getDomainId(), false, Order.class, "id", "batchId", batch.getId());
		
		// 3. 취소 상태 , seq = 0 셋팅 
		for(Order order : orderList) {
			order.setStatus(Order.STATUS_CANCEL);
			order.setJobSeq(0);
		}
		
		// 4. 배치 update
		this.queryManager.updateBatch(orderList, "jobSeq","status");
		cnt += orderList.size();
		
		// 5. 주문 가공 데이터 삭제  
		cnt += this.deleteBatchPreprocessData(batch);
		return cnt;
	}
	
	/**
	 * 주문 데이터 삭제
	 * 
	 * @param batch
	 * @return
	 */
	private int cancelOrderDeleteData(JobBatch batch) {
		int cnt = 0;
		
		// 1. 삭제 조건 생성 
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		
		// 2. 삭제 실행
		cnt+= this.queryManager.deleteList(Order.class, condition);
		
		// 3. 주문 가공 데이터 삭제 
		cnt += this.deleteBatchPreprocessData(batch);
		
		// 4. 배치 삭제 
		this.queryManager.delete(batch);
		
		return cnt;
	}
	
	/**
	 * 주문 가공 데이터 삭제
	 * 
	 * @param batch
	 * @return
	 */
	private int deleteBatchPreprocessData(JobBatch batch) {
		// 1. 삭제 조건 생성 
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		
		// 2. 삭제 실행
		return this.queryManager.deleteList(OrderPreprocess.class, condition);
	}
	
	/**
	 * WMS IF 테이블의 수신완료 데이터 상태 변경
	 * 
	 * @param receipt
	 * @return
	 */
	private void updateWmfIfToReceiptItems(BatchReceiptItem item,String jobDate) {
		Map<String,Object> params = ValueUtil.newMap("wcsBatchNo,wmsBatchNo,stageCd,jobSeq,jobDate",
				item.getWcsBatchNo(),item.getWmsBatchNo(),item.getStageCd(),item.getJobSeq(),jobDate);
 
		this.queryManager.executeBySql(this.batchQueryStore.getWmsIfToReceiptUpdateQuery(), params);
	}
	
}
