package com.example.demo.ticket.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.common.enums.ProductType;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.user.model.User;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

        private final OrderRepository orderRepository;
        private final OrderItemRepository orderItemRepository;
        private final PaymentRepository paymentRepository;
        private final EventBookingRepository eventBookingRepository;
        private final EventPaymentRepository eventPaymentRepository;

        @Transactional(readOnly = true)
        public String generateTicket(String publicId) {

                Long tenantId = TenantContext.getTenantId();

                Order order = orderRepository
                                .findByPublicIdAndTenant_Id(publicId, tenantId)
                                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

                Tenant tenant = order.getTenant();
                Branch branch = order.getBranch();
                User cashier = order.getUser();

                List<OrderItem> items = orderItemRepository.findAllByOrder_Id(order.getId());
                List<OrderItem> activeItems = items.stream()
                                .filter(i -> i.getStatus() == OrderItemStatus.ACTIVE)
                                .toList();

                List<Payment> payments = paymentRepository.findAllByOrder_Id(order.getId());

                BigDecimal totalApplied = payments.stream()
                                .map(Payment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalReceived = payments.stream()
                                .map(p -> p.getAmountReceived() != null
                                                ? p.getAmountReceived()
                                                : p.getAmount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalChange = payments.stream()
                                .map(p -> p.getChangeAmount() != null
                                                ? p.getChangeAmount()
                                                : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                String businessName = tenant.getBusinessName() != null
                                ? tenant.getBusinessName()
                                : "Mi Empresa";
                String logoUrl = tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "";
                String tenantPhone = tenant.getPhone() != null ? tenant.getPhone() : "";
                String tenantWebsite = tenant.getWebsite() != null ? tenant.getWebsite() : "";

                String branchName = branch.getName() != null ? branch.getName() : "";
                String branchAddress = branch.getAddress() != null ? branch.getAddress() : "";
                String branchPhone = branch.getPhone() != null ? branch.getPhone() : "";

                StringBuilder ticket = new StringBuilder();

                ticket.append("""
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
                                .item-detail{font-size:10px; color:#666; margin-left:10px;}
                                img.logo{width:120px; margin:auto; display:block; margin-bottom:5px;}
                                .payment-box{
                                    background:#f5f5f5;
                                    padding:8px;
                                    margin:5px 0;
                                    border-radius:4px;
                                }
                                .change-box{
                                    border:2px solid #000;
                                    padding:8px;
                                    margin:8px 0;
                                    text-align:center;
                                    font-size:16px;
                                    font-weight:bold;
                                }
                                .total-line{font-size:14px; font-weight:bold;}
                                </style>
                                </head>
                                <body>
                                """);

                ticket.append("<div class='center'>");

                if (!logoUrl.isEmpty()) {
                        ticket.append("<img class='logo' src='")
                                        .append(escapeHtml(logoUrl)).append("'/>");
                }

                ticket.append("<div class='bold' style='font-size:14px;'>")
                                .append(escapeHtml(businessName)).append("</div>");

                if (!branchName.isEmpty()) {
                        ticket.append("<div>").append(escapeHtml(branchName)).append("</div>");
                }
                if (!branchAddress.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(branchAddress)).append("</div>");
                }

                String phone = !branchPhone.isEmpty() ? branchPhone : tenantPhone;
                if (!phone.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>Tel: ")
                                        .append(escapeHtml(phone)).append("</div>");
                }
                if (!tenantWebsite.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(tenantWebsite)).append("</div>");
                }

                ticket.append("</div>");

                ticket.append("<div class='line'></div>");

                String orderNumber = String.format("#%06d", order.getId());
                String folio = order.getPublicId().length() > 8
                                ? order.getPublicId().substring(0, 8).toUpperCase()
                                : order.getPublicId().toUpperCase();

                ticket.append("<div class='item'><span>No. de orden:</span><span>")
                                .append(orderNumber).append("</span></div>");
                ticket.append("<div class='item'><span>Folio:</span><span>")
                                .append(folio).append("</span></div>");
                ticket.append("<div class='item'><span>Fecha:</span><span>")
                                .append(order.getCreatedAt().format(
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                                .append("</span></div>");
                ticket.append("<div class='item'><span>Cajero:</span><span>")
                                .append(escapeHtml(cashier.getName())).append("</span></div>");

                if (hasText(order.getCustomerName())) {
                        ticket.append("<div class='item'><span>Cliente:</span><span>")
                                        .append(escapeHtml(order.getCustomerName()))
                                        .append("</span></div>");
                }

                if (order.getClient() != null) {
                        if (hasText(order.getClient().getParentName())) {
                                ticket.append("<div class='item-detail'>")
                                        .append("Padre: ")
                                        .append(escapeHtml(order.getClient().getParentName()))
                                        .append("</div>");
                        }
                        if (hasText(order.getClient().getChildName())) {
                                ticket.append("<div class='item-detail'>")
                                        .append("Niño: ")
                                        .append(escapeHtml(order.getClient().getChildName()))
                                        .append("</div>");
                        }
                }

                // CHILDREN SECTION - Before products
                List<String> uniqueChildren = getUniqueChildren(activeItems);
                if (!uniqueChildren.isEmpty()) {
                        ticket.append("<div class='line'></div>");
                        ticket.append("<div class='center bold'>NIÑOS REGISTRADOS</div>");
                        ticket.append("<div class='line'></div>");
                        for (String childName : uniqueChildren) {
                                ticket.append("<div class='item'>");
                                ticket.append("<span>• ").append(escapeHtml(childName)).append("</span>");
                                ticket.append("</div>");
                        }
                }

                ticket.append("<div class='line'></div>");
                ticket.append("<div class='item bold'><span>Producto</span>"
                                + "<span>Importe</span></div>");
                ticket.append("<div class='line'></div>");

                for (OrderItem item : activeItems) {
                        ticket.append("<div class='item'>");
                        ticket.append("<span>")
                                        .append(escapeHtml(item.getProduct().getName()))
                                        .append("</span>");
                        ticket.append("<span>$")
                                        .append(formatDecimal(item.getSubtotal()))
                                        .append("</span>");
                        ticket.append("</div>");

                        if (Boolean.TRUE.equals(item.getRewardItem())) {
                                ticket.append("<div class='item-detail' style='color:#10b981;'>")
                                                .append("Recompensa lealtad")
                                                .append("</div>");
                        }

                        // Show child name for SERVICE type
                        if (item.getProduct().getType() == ProductType.SERVICE && hasText(item.getChildName())) {
                                ticket.append("<div class='item-detail'>")
                                                .append("Niño: ").append(escapeHtml(item.getChildName()))
                                                .append("</div>");
                        }

                        if (item.getQuantity() > 1) {
                                ticket.append("<div class='item-detail'>")
                                                .append(item.getQuantity()).append(" x $")
                                                .append(formatDecimal(item.getUnitPrice()))
                                                .append("</div>");
                        }

                        // Show session info for timers
                        if (item.getSessionStart() != null && item.getSessionEnd() != null) {
                                ticket.append("<div class='item-detail'>")
                                                .append("Inicio: ").append(item.getSessionStart()
                                                                .format(DateTimeFormatter.ofPattern("HH:mm")))
                                                .append("</div>");
                                ticket.append("<div class='item-detail'>")
                                                .append("Fin: ").append(item.getSessionEnd()
                                                                .format(DateTimeFormatter.ofPattern("HH:mm")))
                                                .append("</div>");
                                if (item.getDurationMinutes() != null) {
                                        ticket.append("<div class='item-detail'>")
                                                        .append("Duración: ").append(item.getDurationMinutes())
                                                        .append(" min</div>");
                                }
                        }
                }

                ticket.append("<div class='line'></div>");

                ticket.append("<div class='item'><span>Subtotal</span><span>$")
                                .append(formatDecimal(order.getSubtotal()))
                                .append("</span></div>");

                if (order.getTax().compareTo(BigDecimal.ZERO) > 0) {
                        ticket.append("<div class='item'><span>IVA</span><span>$")
                                        .append(formatDecimal(order.getTax()))
                                        .append("</span></div>");
                }

                ticket.append("<div class='item total-line'><span>TOTAL</span><span>$")
                                .append(formatDecimal(order.getTotalAmount()))
                                .append("</span></div>");

                ticket.append("<div class='line'></div>");
                ticket.append("<div class='payment-box'>");
                ticket.append("<div class='center bold'>FORMA DE PAGO</div>");

                for (Payment p : payments) {
                        String methodLabel = switch (p.getPaymentMethod()) {
                                case CASH -> "Efectivo";
                                case CARD -> "Tarjeta";
                                case TRANSFER -> "Transferencia";
                        };

                        String methodEmoji = switch (p.getPaymentMethod()) {
                                case CASH -> "💵";
                                case CARD -> "💳";
                                case TRANSFER -> "🏦";
                        };

                        ticket.append("<div class='item'>");
                        ticket.append("<span>").append(methodEmoji).append(" ")
                                        .append(methodLabel).append("</span>");
                        ticket.append("<span>$")
                                        .append(formatDecimal(p.getAmount()))
                                        .append("</span>");
                        ticket.append("</div>");

                        if (p.getAmountReceived() != null
                                        && p.getAmountReceived().compareTo(p.getAmount()) > 0) {
                                ticket.append("<div class='item-detail'>Recibido: $")
                                                .append(formatDecimal(p.getAmountReceived()))
                                                .append("</div>");
                        }

                        if (hasText(p.getReference())) {
                                ticket.append("<div class='item-detail'>Ref: ")
                                                .append(escapeHtml(p.getReference()))
                                                .append("</div>");
                        }
                }

                ticket.append("<div class='line'></div>");

                ticket.append("<div class='item'><span>Monto recibido</span><span>$")
                                .append(formatDecimal(totalReceived))
                                .append("</span></div>");

                ticket.append("<div class='item bold'><span>Total pagado</span><span>$")
                                .append(formatDecimal(totalApplied))
                                .append("</span></div>");

                ticket.append("</div>");

                if (totalChange.compareTo(BigDecimal.ZERO) > 0) {
                        ticket.append("<div class='change-box'>");
                        ticket.append("💰 CAMBIO: $").append(formatDecimal(totalChange));
                        ticket.append("</div>");
                }

                ticket.append("<div class='line'></div>");
                ticket.append("<div class='center'>");
                ticket.append("<div>¡Gracias por tu compra!</div>");
                ticket.append("<div style='font-size:10px;'>Esperamos verte pronto</div>");

                if (!tenantWebsite.isEmpty()) {
                        ticket.append("<br><img src='https://api.qrserver.com/v1/"
                                        + "create-qr-code/?size=100x100&data=")
                                        .append(escapeHtml(tenantWebsite)).append("'/>");
                }

                ticket.append("<div style='font-size:9px; margin-top:8px; color:#999;'>")
                                .append(LocalDateTime.now().format(
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                                .append("</div>");

                ticket.append("</div>");
                ticket.append("</body></html>");

                return ticket.toString();
        }

        @Transactional(readOnly = true)
        public String generateEventTicket(String eventPublicId) {

                Long tenantId = TenantContext.getTenantId();

                EventBooking event = eventBookingRepository
                                .findByPublicIdAndTenant_Id(eventPublicId, tenantId)
                                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

                Tenant tenant = event.getTenant();
                Branch branch = event.getBranch();

                List<EventPayment> payments = eventPaymentRepository
                                .findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtDesc(
                                                eventPublicId, tenantId);

                String businessName = tenant.getBusinessName() != null
                                ? tenant.getBusinessName()
                                : "Mi Empresa";
                String logoUrl = tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "";
                String tenantPhone = tenant.getPhone() != null ? tenant.getPhone() : "";
                String tenantWebsite = tenant.getWebsite() != null ? tenant.getWebsite() : "";

                String branchName = branch.getName() != null ? branch.getName() : "";
                String branchAddress = branch.getAddress() != null ? branch.getAddress() : "";
                String branchPhone = branch.getPhone() != null ? branch.getPhone() : "";

                String eventNumber = String.format("EV-%06d", event.getEventNumber());
                String folio = event.getPublicId().length() > 8
                                ? event.getPublicId().substring(0, 8).toUpperCase()
                                : event.getPublicId().toUpperCase();

                StringBuilder ticket = new StringBuilder();

                ticket.append("""
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
                                .item-detail{font-size:10px; color:#666; margin-left:10px;}
                                img.logo{width:120px; margin:auto; display:block; margin-bottom:5px;}
                                .payment-box{
                                    background:#f5f5f5;
                                    padding:8px;
                                    margin:5px 0;
                                    border-radius:4px;
                                }
                                .section-title{
                                    font-weight:bold;
                                    text-align:center;
                                    margin:4px 0;
                                }
                                </style>
                                </head>
                                <body>
                                """);

                // HEADER
                ticket.append("<div class='center'>");

                if (!logoUrl.isEmpty()) {
                        ticket.append("<img class='logo' src='")
                                        .append(escapeHtml(logoUrl)).append("'/>");
                }

                ticket.append("<div class='bold' style='font-size:14px;'>")
                                .append(escapeHtml(businessName)).append("</div>");

                if (!branchName.isEmpty()) {
                        ticket.append("<div>").append(escapeHtml(branchName)).append("</div>");
                }
                if (!branchAddress.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(branchAddress)).append("</div>");
                }

                String phone = !branchPhone.isEmpty() ? branchPhone : tenantPhone;
                if (!phone.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>Tel: ")
                                        .append(escapeHtml(phone)).append("</div>");
                }
                if (!tenantWebsite.isEmpty()) {
                        ticket.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(tenantWebsite)).append("</div>");
                }

                ticket.append("</div>");
                ticket.append("<div class='line'></div>");

                // EVENT INFO
                ticket.append("<div class='section-title'>COMPROBANTE DE EVENTO</div>");
                ticket.append("<div class='line'></div>");

                ticket.append("<div class='item'><span>Evento:</span><span>")
                                .append(eventNumber).append("</span></div>");
                ticket.append("<div class='item'><span>Folio:</span><span>")
                                .append(folio).append("</span></div>");
                ticket.append("<div class='item'><span>Estado:</span><span>")
                                .append(formatEventStatus(event.getStatus()))
                                .append("</span></div>");

                ticket.append("<div class='line'></div>");

                // CLIENT / CHILD
                ticket.append("<div class='item'><span>Cliente:</span><span>")
                                .append(escapeHtml(event.getCustomerName()))
                                .append("</span></div>");

                if (hasText(event.getPhone())) {
                        ticket.append("<div class='item'><span>Teléfono:</span><span>")
                                        .append(escapeHtml(event.getPhone()))
                                        .append("</span></div>");
                }

                ticket.append("<div class='item'><span>Niño:</span><span>")
                                .append(escapeHtml(event.getChildName()))
                                .append("</span></div>");

                if (event.getChildAge() != null) {
                        ticket.append(
                                        "<div class='item'><span>Edad:</span><span>")
                                        .append(event.getChildAge())
                                        .append(" años</span></div>");
                }

                String packageName = event.getPackageProduct() != null
                                ? event.getPackageProduct().getName()
                                : "—";
                ticket.append("<div class='item'><span>Paquete:</span><span>")
                                .append(escapeHtml(packageName)).append("</span></div>");

                ticket.append("<div class='line'></div>");

                // DATE / TIME
                ticket.append("<div class='item'><span>Fecha:</span><span>")
                                .append(event.getEventDate().format(
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                .append("</span></div>");
                ticket.append("<div class='item'><span>Horario:</span><span>")
                                .append(event.getStartTime().format(
                                                DateTimeFormatter.ofPattern("HH:mm")))
                                .append(" - ")
                                .append(event.getEndTime().format(
                                                DateTimeFormatter.ofPattern("HH:mm")))
                                .append("</span></div>");

                // GUESTS
                StringBuilder guestLine = new StringBuilder();
                if (event.getGuestChildren() != null) {
                        guestLine.append("Niños: ").append(event.getGuestChildren());
                }
                if (event.getGuestAdults() != null) {
                        if (guestLine.length() > 0)
                                guestLine.append(" · ");
                        guestLine.append("Adultos: ").append(event.getGuestAdults());
                }
                if (guestLine.length() > 0) {
                        ticket.append("<div class='item'><span>Invitados:</span><span>")
                                        .append(guestLine).append("</span></div>");
                }

                ticket.append("<div class='line'></div>");

                // FINANCIAL SUMMARY
                ticket.append("<div class='section-title'>RESUMEN FINANCIERO</div>");
                ticket.append("<div class='line'></div>");

                ticket.append("<div class='item'><span>Total evento:</span><span>$")
                                .append(formatDecimal(event.getEventPrice()))
                                .append("</span></div>");
                ticket.append("<div class='item'><span>Total pagado:</span><span>$")
                                .append(formatDecimal(event.getDepositAmount()))
                                .append("</span></div>");

                BigDecimal remaining = event.getRemainingAmount();
                ticket.append("<div class='item bold'><span>Saldo restante:</span><span>$")
                                .append(formatDecimal(remaining))
                                .append("</span></div>");

                if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                        ticket.append("<div class='center bold' style='color:#10b981; margin-top:4px;'>")
                                        .append("EVENTO LIQUIDADO").append("</div>");
                }

                // PAYMENT HISTORY
                if (!payments.isEmpty()) {
                        ticket.append("<div class='line'></div>");
                        ticket.append("<div class='section-title'>HISTORIAL DE PAGOS</div>");
                        ticket.append("<div class='line'></div>");

                        for (EventPayment p : payments) {
                                String methodLabel = switch (p.getPaymentMethod()) {
                                        case CASH -> "Efectivo";
                                        case CARD -> "Tarjeta";
                                        case TRANSFER -> "Transferencia";
                                };

                                String methodEmoji = switch (p.getPaymentMethod()) {
                                        case CASH -> "💵";
                                        case CARD -> "💳";
                                        case TRANSFER -> "🏦";
                                };

                                ticket.append("<div class='payment-box'>");
                                ticket.append("<div style='font-size:10px;'>")
                                                .append(p.getPaidAt().format(
                                                                DateTimeFormatter.ofPattern(
                                                                                "dd/MM/yyyy HH:mm")))
                                                .append("</div>");

                                ticket.append("<div class='item'><span>")
                                                .append(methodEmoji).append(" ")
                                                .append(methodLabel).append("</span>");
                                ticket.append("<span>$")
                                                .append(formatDecimal(p.getAmount()))
                                                .append("</span></div>");

                                if (hasText(p.getReference())) {
                                        ticket.append("<div class='item-detail'>Ref: ")
                                                        .append(escapeHtml(p.getReference()))
                                                        .append("</div>");
                                }

                                if (hasText(p.getReceivedByUserEmail())) {
                                        ticket.append(
                                                        "<div class='item-detail'>Recibió: ")
                                                        .append(escapeHtml(
                                                                        p.getReceivedByUserEmail()))
                                                        .append("</div>");
                                }

                                if (hasText(p.getNotes())) {
                                        ticket.append("<div class='item-detail'>")
                                                        .append(escapeHtml(p.getNotes()))
                                                        .append("</div>");
                                }

                                ticket.append("</div>");
                        }
                }

                // NOTES
                if (hasText(event.getNotes())) {
                        ticket.append("<div class='line'></div>");
                        ticket.append("<div class='section-title'>NOTAS</div>");
                        ticket.append("<div class='line'></div>");
                        ticket.append("<div style='font-size:10px; white-space:normal; word-wrap:break-word;'>")
                                        .append(escapeHtml(event.getNotes()))
                                        .append("</div>");
                }

                // FOOTER & QR
                ticket.append("<div class='line'></div>");
                ticket.append("<div class='center'>");
                ticket.append("<div>¡Gracias por elegirnos!</div>");

                if (!tenantWebsite.isEmpty()) {
                        ticket.append("<br><img src='https://api.qrserver.com/v1/"
                                        + "create-qr-code/?size=100x100&data=")
                                        .append(escapeHtml(tenantWebsite)).append("'/>");
                }

                ticket.append("<div style='font-size:9px; margin-top:8px; color:#999;'>")
                                .append("Comprobante generado: ")
                                .append(LocalDateTime.now().format(
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                                .append("</div>");

                ticket.append("</div>");
                ticket.append("</body></html>");

                return ticket.toString();
        }

        @Transactional(readOnly = true)
        public String generateEventPaymentReceipt(String eventPublicId, String paymentPublicId) {

                Long tenantId = TenantContext.getTenantId();

                EventPayment payment = eventPaymentRepository
                                .findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                                                paymentPublicId, eventPublicId, tenantId)
                                .orElseThrow(() -> new EntityNotFoundException("Pago de evento no encontrado"));

                EventBooking event = payment.getEventBooking();
                Tenant tenant = event.getTenant();
                Branch branch = event.getBranch();

                List<EventPayment> orderedPayments = eventPaymentRepository
                                .findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtAscIdAsc(
                                                eventPublicId, tenantId);

                BigDecimal paymentsBefore = BigDecimal.ZERO;
                boolean targetFound = false;
                for (EventPayment current : orderedPayments) {
                        if (current.getId().equals(payment.getId())) {
                                targetFound = true;
                                break;
                        }
                        paymentsBefore = paymentsBefore.add(current.getAmount());
                }

                if (!targetFound) {
                        throw new EntityNotFoundException("Pago de evento no encontrado");
                }

                // Pagos legacy pueden no contar con snapshot; en ese caso solo es posible
                // reconstruir contra el precio actual conocido del evento.
                BigDecimal receiptEventPrice = payment.getEventPriceAtPayment() != null
                                ? payment.getEventPriceAtPayment()
                                : event.getEventPrice();
                BigDecimal previousBalance = receiptEventPrice.subtract(paymentsBefore);
                BigDecimal totalPaidAfter = paymentsBefore.add(payment.getAmount());
                BigDecimal newBalance = receiptEventPrice.subtract(totalPaidAfter);

                String businessName = tenant.getBusinessName() != null
                                ? tenant.getBusinessName()
                                : "Mi Empresa";
                String logoUrl = tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "";
                String tenantPhone = tenant.getPhone() != null ? tenant.getPhone() : "";
                String tenantWebsite = tenant.getWebsite() != null ? tenant.getWebsite() : "";

                String branchName = branch.getName() != null ? branch.getName() : "";
                String branchAddress = branch.getAddress() != null ? branch.getAddress() : "";
                String branchPhone = branch.getPhone() != null ? branch.getPhone() : "";

                String eventNumber = String.format("EV-%06d", event.getEventNumber());
                String eventFolio = event.getPublicId().length() > 8
                                ? event.getPublicId().substring(0, 8).toUpperCase()
                                : event.getPublicId().toUpperCase();
                String receiptNumber = String.format("RP-%06d", payment.getId());
                String packageName = event.getPackageProduct() != null
                                ? event.getPackageProduct().getName()
                                : "—";

                StringBuilder receipt = new StringBuilder();
                receipt.append("""
                                <html>
                                <head>
                                <meta charset="UTF-8">
                                <style>
                                body{
                                    font-family:'Courier New',monospace;
                                    width:300px;
                                    margin:0 auto;
                                    padding:10px;
                                    font-size:12px;
                                }
                                .center{text-align:center;}
                                .bold{font-weight:bold;}
                                .line{border-top:1px dashed #333;margin:8px 0;}
                                .item{display:flex;justify-content:space-between;gap:12px;margin:3px 0;}
                                .item span:last-child{text-align:right;overflow-wrap:anywhere;}
                                .section-title{font-weight:bold;text-align:center;margin:4px 0;}
                                .notes{font-size:10px;white-space:pre-wrap;overflow-wrap:anywhere;}
                                img.logo{width:120px;margin:auto;display:block;margin-bottom:5px;}
                                .paid{border:2px solid #10b981;color:#047857;padding:7px;margin-top:8px;text-align:center;font-weight:bold;}
                                </style>
                                </head>
                                <body>
                                """);

                receipt.append("<div class='center'>");
                if (!logoUrl.isEmpty()) {
                        receipt.append("<img class='logo' src='")
                                        .append(escapeHtml(logoUrl)).append("'/>");
                }
                receipt.append("<div class='bold' style='font-size:14px;'>")
                                .append(escapeHtml(businessName)).append("</div>");
                if (!branchName.isEmpty()) {
                        receipt.append("<div>").append(escapeHtml(branchName)).append("</div>");
                }
                if (!branchAddress.isEmpty()) {
                        receipt.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(branchAddress)).append("</div>");
                }
                String phone = !branchPhone.isEmpty() ? branchPhone : tenantPhone;
                if (!phone.isEmpty()) {
                        receipt.append("<div style='font-size:10px;'>Tel: ")
                                        .append(escapeHtml(phone)).append("</div>");
                }
                if (!tenantWebsite.isEmpty()) {
                        receipt.append("<div style='font-size:10px;'>")
                                        .append(escapeHtml(tenantWebsite)).append("</div>");
                }
                receipt.append("</div>");

                receipt.append("<div class='line'></div>")
                                .append("<div class='section-title'>RECIBO DE PAGO</div>")
                                .append("<div class='line'></div>");

                appendReceiptItem(receipt, "Evento:", eventNumber, false);
                appendReceiptItem(receipt, "Folio evento:", eventFolio, false);
                appendReceiptItem(receipt, "Recibo:", receiptNumber, true);
                appendReceiptItem(receipt, "Fecha:", payment.getPaidAt().format(
                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), false);

                receipt.append("<div class='line'></div>");
                appendReceiptItem(receipt, "Cliente:", event.getCustomerName(), true);
                appendReceiptItem(receipt, "Niño:", event.getChildName(), true);
                appendReceiptItem(receipt, "Paquete:", packageName, true);
                appendReceiptItem(receipt, "Fecha evento:", event.getEventDate().format(
                                DateTimeFormatter.ofPattern("dd/MM/yyyy")), false);

                receipt.append("<div class='line'></div>")
                                .append("<div class='section-title'>PAGO RECIBIDO</div>")
                                .append("<div class='line'></div>");
                appendReceiptItem(receipt, "Monto:", "$" + formatDecimal(payment.getAmount()), false);
                appendReceiptItem(receipt, "Método:", formatPaymentMethod(payment), false);
                if (hasText(payment.getReference())) {
                        appendReceiptItem(receipt, "Referencia:", payment.getReference(), true);
                }
                if (hasText(payment.getReceivedByUserEmail())) {
                        receipt.append("<div style='margin-top:7px;'>Recibió:</div>")
                                        .append("<div class='notes'>")
                                        .append(escapeHtml(payment.getReceivedByUserEmail()))
                                        .append("</div>");
                }
                if (hasText(payment.getNotes())) {
                        receipt.append("<div class='line'></div>")
                                        .append("<div class='section-title'>NOTAS DEL PAGO</div>")
                                        .append("<div class='notes'>")
                                        .append(escapeHtml(payment.getNotes()))
                                        .append("</div>");
                }

                receipt.append("<div class='line'></div>")
                                .append("<div class='section-title'>ESTADO FINANCIERO</div>")
                                .append("<div class='line'></div>");
                appendReceiptItem(receipt, "Total evento:", "$" + formatDecimal(receiptEventPrice), false);
                appendReceiptItem(receipt, "Saldo anterior:", "$" + formatDecimal(previousBalance), false);
                appendReceiptItem(receipt, "Este pago:", "$" + formatDecimal(payment.getAmount()), false);
                appendReceiptItem(receipt, "Total pagado:", "$" + formatDecimal(totalPaidAfter), false);
                appendReceiptItem(receipt, "Saldo nuevo:", "$" + formatDecimal(newBalance), false);

                if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
                        receipt.append("<div class='paid'>EVENTO LIQUIDADO CON ESTE PAGO</div>");
                }

                receipt.append("<div class='line'></div><div class='center'>");
                if (!tenantWebsite.isEmpty()) {
                        receipt.append("<img src='https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=")
                                        .append(escapeHtml(tenantWebsite)).append("'/><br>");
                }
                receipt.append("<div>¡Gracias por su pago!</div>")
                                .append("</div></body></html>");

                return receipt.toString();
        }

        private void appendReceiptItem(StringBuilder receipt, String label, String value, boolean escapeValue) {
                receipt.append("<div class='item'><span>")
                                .append(label)
                                .append("</span><span>")
                                .append(escapeValue ? escapeHtml(value) : value)
                                .append("</span></div>");
        }

        private String formatPaymentMethod(EventPayment payment) {
                return switch (payment.getPaymentMethod()) {
                        case CASH -> "Efectivo";
                        case CARD -> "Tarjeta";
                        case TRANSFER -> "Transferencia";
                };
        }

        private String formatEventStatus(EventStatus status) {
                return switch (status) {
                        case PENDING_DEPOSIT -> "Pendiente de pago";
                        case CONFIRMED -> "Confirmado";
                        case IN_PROGRESS -> "En curso";
                        case COMPLETED -> "Completado";
                        case CANCELLED -> "Cancelado";
                };
        }

        private List<String> getUniqueChildren(List<OrderItem> items) {
                Set<String> uniqueChildren = new LinkedHashSet<>();

                for (OrderItem item : items) {
                        String childName = item.getChildName();
                        if (hasText(childName)) {
                                uniqueChildren.add(childName);
                        }
                }

                return List.copyOf(uniqueChildren);
        }

        private boolean hasText(String value) {
                return value != null && !value.trim().isEmpty();
        }

        private String formatDecimal(BigDecimal value) {
                if (value == null)
                        return "0.00";
                return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private String escapeHtml(String text) {
                if (text == null)
                        return "";
                return text
                                .replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                                .replace("\"", "&quot;")
                                .replace("'", "&#39;");
        }
}
