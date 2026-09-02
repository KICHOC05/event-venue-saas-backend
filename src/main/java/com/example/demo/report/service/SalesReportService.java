package com.example.demo.report.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.dashboard.dto.StatsResponse;
import com.example.demo.dashboard.dto.TopItemDTO;
import com.example.demo.dashboard.service.DashboardService;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SalesReportService {

    private final DashboardService dashboardService;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int TOP_PRODUCTS_LIMIT = 5;

    public String generateSalesReportTicket(ReportPeriod period) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        int rangeDays = period.getRangeDays();
        StatsResponse stats = dashboardService.getStats(rangeDays);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId).orElse(null);

        return buildHtml(stats, period.getLabel(), tenant, branch);
    }

    private String buildHtml(StatsResponse stats, String periodLabel, Tenant tenant, Branch branch) {
        String businessName = tenant.getBusinessName() != null
                ? tenant.getBusinessName()
                : "Sin nombre";
        String logoUrl = tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "";
        String tenantPhone = tenant.getPhone() != null ? tenant.getPhone() : "";
        String tenantWebsite = tenant.getWebsite() != null ? tenant.getWebsite() : "";

        String branchName = branch != null && branch.getName() != null
                ? branch.getName() : "";
        String branchAddress = branch != null && branch.getAddress() != null
                ? branch.getAddress() : "";
        String branchPhone = branch != null && branch.getPhone() != null
                ? branch.getPhone() : "";

        boolean hasSales = stats.getTotalSales() != null
                && stats.getTotalSales().compareTo(BigDecimal.ZERO) > 0;

        StringBuilder out = new StringBuilder();
        out.append("""
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                body{
                    font-family: 'Courier New', monospace;
                    width: 300px;
                    margin: 0 auto;
                    padding: 10px;
                    font-size: 12px;
                }
                .center{text-align:center;}
                .right{text-align:right;}
                .bold{font-weight:bold;}
                .line{border-top:1px dashed #333; margin:8px 0;}
                .item{display:flex; justify-content:space-between; margin:2px 0;}
                .item span:first-child{
                    max-width: 185px;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }
                .item span:last-child{
                    text-align: right;
                    white-space: nowrap;
                    margin-left: 6px;
                }
                .item-detail{font-size:10px; color:#666; margin-left:10px;}
                img.logo{width:120px; margin:auto; display:block; margin-bottom:5px;}
                .section-title{text-align:center; font-weight:bold; margin:8px 0 4px;}
                .total-line{font-size:14px; font-weight:bold;}
                @media print {
                  @page {
                    size: 80mm auto;
                    margin: 0;
                  }
                  html, body {
                    width: 80mm;
                    margin: 0;
                    padding: 0;
                  }
                  body {
                    padding: 3mm;
                  }
                }
                </style>
                </head>
                <body>
                """);

        out.append("<div class='center'>");

        if (!logoUrl.isEmpty()) {
            out.append("<img class='logo' src='").append(escape(logoUrl)).append("'/>");
        }

        out.append("<div class='bold' style='font-size:14px;'>")
                .append(escape(businessName)).append("</div>");

        if (!branchName.isEmpty()) {
            out.append("<div>").append(escape(branchName)).append("</div>");
        }
        if (!branchAddress.isEmpty()) {
            out.append("<div style='font-size:10px;'>")
                    .append(escape(branchAddress)).append("</div>");
        }

        String phone = !branchPhone.isEmpty() ? branchPhone : tenantPhone;
        if (!phone.isEmpty()) {
            out.append("<div style='font-size:10px;'>Tel: ")
                    .append(escape(phone)).append("</div>");
        }
        if (!tenantWebsite.isEmpty()) {
            out.append("<div style='font-size:10px;'>")
                    .append(escape(tenantWebsite)).append("</div>");
        }

        out.append("</div>");
        out.append("<div class='line'></div>");

        out.append("<div class='center'><span class='bold'>REPORTE DE VENTAS</span></div>");
        out.append("<div class='center'>").append(escape(periodLabel)).append("</div>");
        out.append("<div class='center'>").append(stats.getDateFrom())
                .append(" - ").append(stats.getDateTo()).append("</div>");
        out.append("<div class='center' style='font-size:10px;'>Generado: ")
                .append(LocalDateTime.now().format(DATE_TIME_FMT)).append("</div>");
        out.append("<div class='line'></div>");

        out.append("<div class='center' style='margin:8px 0;'>");
        out.append("<div class='item'><span>Total ventas:</span><span class='bold'>$")
                .append(formatDecimal(stats.getTotalSales())).append("</span></div>");
        out.append("<div class='item'><span>Ordenes:</span><span>")
                .append(stats.getTotalOrders()).append("</span></div>");

        BigDecimal avgTicket = stats.getAverageTicket() != null
                ? stats.getAverageTicket() : BigDecimal.ZERO;
        out.append("<div class='item'><span>Ticket promedio:</span><span>$")
                .append(formatDecimal(avgTicket)).append("</span></div>");

        Double growth = stats.getGrowthPercentage() != null
                ? stats.getGrowthPercentage() : 0.0;
        String growthSign = growth >= 0 ? "+" : "";
        out.append("<div class='item'><span>Crecimiento:</span><span>")
                .append(growthSign).append(String.format("%.1f%%", growth))
                .append("</span></div>");
        out.append("</div>");

        if (!hasSales) {
            out.append("<div class='line'></div>");
            out.append("<div class='center bold' style='padding:16px 0;'>SIN VENTAS EN ESTE PERIODO</div>");
            out.append("<div class='line'></div>");
        } else {
            if (stats.getPaymentBreakdown() != null) {
                StatsResponse.PaymentBreakdown pb = stats.getPaymentBreakdown();
                out.append("<div class='line'></div>");
                out.append("<div class='section-title'>DESGLOSE DE PAGOS</div>");
                out.append("<div class='line'></div>");
                out.append("<div class='item'><span>Efectivo</span><span>$")
                        .append(formatDecimal(pb.getCashTotal()))
                        .append("</span></div>");
                out.append("<div class='item'><span>Tarjeta</span><span>$")
                        .append(formatDecimal(pb.getCardTotal()))
                        .append("</span></div>");
                out.append("<div class='item'><span>Transferencia</span><span>$")
                        .append(formatDecimal(pb.getTransferTotal()))
                        .append("</span></div>");
            }

            if (stats.getTopProducts() != null && !stats.getTopProducts().isEmpty()) {
                out.append("<div class='line'></div>");
                out.append("<div class='section-title'>TOP PRODUCTOS</div>");
                out.append("<div class='line'></div>");
                int rank = 1;
                for (TopItemDTO p : stats.getTopProducts()) {
                    if (rank > TOP_PRODUCTS_LIMIT) break;
                    out.append("<div class='item'><span>#")
                            .append(rank++).append(" ")
                            .append(escape(p.getName()))
                            .append("</span><span>$")
                            .append(formatDecimal(p.getTotalRevenue()))
                            .append("</span></div>");
                    out.append("<div class='item-detail'>Cant: ")
                            .append(p.getQuantitySold()).append("</div>");
                }
            }

            if (stats.getSalesByPackage() != null && !stats.getSalesByPackage().isEmpty()) {
                out.append("<div class='line'></div>");
                out.append("<div class='section-title'>VENTAS POR PAQUETE</div>");
                out.append("<div class='line'></div>");
                for (TopItemDTO p : stats.getSalesByPackage()) {
                    out.append("<div class='item'><span>")
                            .append(escape(p.getName()))
                            .append("</span><span>$")
                            .append(formatDecimal(p.getTotalRevenue()))
                            .append("</span></div>");
                    out.append("<div class='item-detail'>Cant: ")
                            .append(p.getQuantitySold()).append("</div>");
                }
            }
        }

        out.append("<div class='line'></div>");
        out.append("<div class='center'>");
        out.append("<div>Solo uso administrativo</div>");
        out.append("<div style='font-size:10px;'>SalonEventos SaaS</div>");
        out.append("<div style='font-size:9px; margin-top:4px; color:#999;'>")
                .append(LocalDateTime.now().format(DATE_TIME_FMT))
                .append("</div>");
        out.append("</div>");

        out.append("</body></html>");

        return out.toString();
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public enum ReportPeriod {
        WEEKLY("Semanal", 7),
        BIWEEKLY("Quincenal", 15),
        MONTHLY("Mensual", 30);

        private final String label;
        private final int rangeDays;

        ReportPeriod(String label, int rangeDays) {
            this.label = label;
            this.rangeDays = rangeDays;
        }

        public String getLabel() { return label; }
        public int getRangeDays() { return rangeDays; }
    }
}
