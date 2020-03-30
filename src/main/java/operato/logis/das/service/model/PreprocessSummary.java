package operato.logis.das.service.model;

/**
 * 출고 / 반품 주문가공 서머리
 * 
 * @author shortstop
 */
public class PreprocessSummary {
	/**
	 * 총 주문 수 (출고 : 매장 수, 반품 : 상품 수)
	 */
	private String totalOrderCnt;
	/**
	 * 할당된 주문 수
	 */
	private String assignedOrderCnt;
	/**
	 * 남은 주문 수
	 */
	private String remainOrderCnt;
	/**
	 * 총 주문 PCS
	 */
	private String totalOrderPcs;
	/**
	 * 할당된 주문 PCS
	 */
	private String assignedOrderPcs;
	/**
	 * 남은 주문 PCS
	 */
	private String remainOrderPcs;
	
	public String getTotalOrderCnt() {
		return totalOrderCnt;
	}

	public void setTotalOrderCnt(String totalOrderCnt) {
		this.totalOrderCnt = totalOrderCnt;
	}

	public String getAssignedOrderCnt() {
		return assignedOrderCnt;
	}

	public void setAssignedOrderCnt(String assignedOrderCnt) {
		this.assignedOrderCnt = assignedOrderCnt;
	}

	public String getRemainOrderCnt() {
		return remainOrderCnt;
	}

	public void setRemainOrderCnt(String remainOrderCnt) {
		this.remainOrderCnt = remainOrderCnt;
	}

	public String getTotalOrderPcs() {
		return totalOrderPcs;
	}

	public void setTotalOrderPcs(String totalOrderPcs) {
		this.totalOrderPcs = totalOrderPcs;
	}

	public String getAssignedOrderPcs() {
		return assignedOrderPcs;
	}

	public void setAssignedOrderPcs(String assignedOrderPcs) {
		this.assignedOrderPcs = assignedOrderPcs;
	}

	public String getRemainOrderPcs() {
		return remainOrderPcs;
	}

	public void setRemainOrderPcs(String remainOrderPcs) {
		this.remainOrderPcs = remainOrderPcs;
	}

}
