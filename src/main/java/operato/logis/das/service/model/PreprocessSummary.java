package operato.logis.das.service.model;

/**
 * 출고 / 반품 주문가공 서머리
 * 
 * @author shortstop
 */
public class PreprocessSummary {
	/**
	 * 총 SKU 수
	 */
	private String totalSkus;
	/**
	 * 할당된 SKU 수
	 */
	private String assignedSku;
	/**
	 * 남은 SKU 수
	 */
	private String remainSku;
	/**
	 * 총 주문 PCS
	 */
	private String totalOrdersPcs;
	/**
	 * 할당된 PCS
	 */
	private String assignedPcs;
	/**
	 * 남은 PCS
	 */
	private String remainPcs;
	
	public String getTotalSkus() {
		return totalSkus;
	}

	public void setTotalSkus(String totalSkus) {
		this.totalSkus = totalSkus;
	}

	public String getAssignedSku() {
		return assignedSku;
	}

	public void setAssignedSku(String assignedSku) {
		this.assignedSku = assignedSku;
	}

	public String getRemainSku() {
		return remainSku;
	}

	public void setRemainSku(String remainSku) {
		this.remainSku = remainSku;
	}

	public String getTotalOrdersPcs() {
		return totalOrdersPcs;
	}

	public void setTotalOrdersPcs(String totalOrdersPcs) {
		this.totalOrdersPcs = totalOrdersPcs;
	}

	public String getAssignedPcs() {
		return assignedPcs;
	}

	public void setAssignedPcs(String assignedPcs) {
		this.assignedPcs = assignedPcs;
	}

	public String getRemainPcs() {
		return remainPcs;
	}

	public void setRemainPcs(String remainPcs) {
		this.remainPcs = remainPcs;
	}

}
