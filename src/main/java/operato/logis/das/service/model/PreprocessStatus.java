package operato.logis.das.service.model;

/**
 * 출고 / 반품 주문 가공 정보
 * 
 * @author shortstop
 */
public class PreprocessStatus {
	/**
	 * 상품 코드 
	 */
	private String cellAssgnCd;
	/**
	 * 호기 코드 
	 */
	private String equipCd;
	/**
	 * 주문 상품 수량 
	 */
	private int orderSkuQty;
	/**
	 * 주문 수량 총 PCS 
	 */
	private int orderPcsQty;
	
	
	public PreprocessStatus() {
	}
	
	public PreprocessStatus(String cellAssgnCd, String equipCd, Integer orderSkuQty, Integer orderPcsQty) {
		this.cellAssgnCd = cellAssgnCd;
		this.equipCd = equipCd;
		this.orderSkuQty = orderSkuQty;
		this.orderPcsQty = orderPcsQty;
	}

	public String getCellAssgnCd() {
		return cellAssgnCd;
	}

	public void setCellAssgnCd(String cellAssgnCd) {
		this.cellAssgnCd = cellAssgnCd;
	}

	public String getEquipCd() {
		return equipCd;
	}

	public void setEquipCd(String equipCd) {
		this.equipCd = equipCd;
	}

	public int getOrderSkuQty() {
		return orderSkuQty;
	}

	public void setOrderSkuQty(int orderSkuQty) {
		this.orderSkuQty = orderSkuQty;
	}

	public int getOrderPcsQty() {
		return orderPcsQty;
	}

	public void setOrderPcsQty(int orderPcsQty) {
		this.orderPcsQty = orderPcsQty;
	}
	
}
