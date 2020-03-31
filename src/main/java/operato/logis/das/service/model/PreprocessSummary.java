package operato.logis.das.service.model;

/**
 * 출고 / 반품 주문가공 서머리
 * 
 * @author shortstop
 */
public class PreprocessSummary {
	/**
	 * 총 셀 수
	 */
	private Integer totalCellCnt;
	/**
	 * 총 주문 수 (출고 : 매장 수, 반품 : 상품 수)
	 */
	private Integer totalOrderCnt;
	/**
	 * 할당된 셀 수
	 */
	private Integer assignedCellCnt;
	/**
	 * 할당된 주문 수
	 */
	private Integer assignedOrderCnt;
	/**
	 * 남은 셀 수
	 */
	private Integer remainCellCnt;
	/**
	 * 남은 주문 수
	 */
	private Integer remainOrderCnt;
	/**
	 * 총 주문 PCS
	 */
	private Integer totalOrderPcs;
	/**
	 * 할당된 주문 PCS
	 */
	private Integer assignedOrderPcs;
	/**
	 * 남은 주문 PCS
	 */
	private Integer remainOrderPcs;
	
	public Integer getTotalCellCnt() {
		return totalCellCnt;
	}
	
	public void setTotalCellCnt(Integer totalCellCnt) {
		this.totalCellCnt = totalCellCnt;
	}
	
	public Integer getTotalOrderCnt() {
		return totalOrderCnt;
	}
	
	public void setTotalOrderCnt(Integer totalOrderCnt) {
		this.totalOrderCnt = totalOrderCnt;
	}
	
	public Integer getAssignedCellCnt() {
		return assignedCellCnt;
	}
	
	public void setAssignedCellCnt(Integer assignedCellCnt) {
		this.assignedCellCnt = assignedCellCnt;
	}
	
	public Integer getAssignedOrderCnt() {
		return assignedOrderCnt;
	}
	
	public void setAssignedOrderCnt(Integer assignedOrderCnt) {
		this.assignedOrderCnt = assignedOrderCnt;
	}
	
	public Integer getRemainCellCnt() {
		return remainCellCnt;
	}
	
	public void setRemainCellCnt(Integer remainCellCnt) {
		this.remainCellCnt = remainCellCnt;
	}
	
	public Integer getRemainOrderCnt() {
		return remainOrderCnt;
	}
	
	public void setRemainOrderCnt(Integer remainOrderCnt) {
		this.remainOrderCnt = remainOrderCnt;
	}
	
	public Integer getTotalOrderPcs() {
		return totalOrderPcs;
	}
	
	public void setTotalOrderPcs(Integer totalOrderPcs) {
		this.totalOrderPcs = totalOrderPcs;
	}
	
	public Integer getAssignedOrderPcs() {
		return assignedOrderPcs;
	}
	
	public void setAssignedOrderPcs(Integer assignedOrderPcs) {
		this.assignedOrderPcs = assignedOrderPcs;
	}
	
	public Integer getRemainOrderPcs() {
		return remainOrderPcs;
	}
	
	public void setRemainOrderPcs(Integer remainOrderPcs) {
		this.remainOrderPcs = remainOrderPcs;
	}

}
