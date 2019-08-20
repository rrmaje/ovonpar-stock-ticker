package actors.oe;

interface EventVisitor {

    void visit(Event.OrderAccepted event);

    void visit(Event.OrderRejected event);

    void visit(Event.OrderExecuted event);

    void visit(Event.OrderCanceled event);

}
