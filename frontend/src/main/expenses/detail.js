import React, {useEffect, useState} from "react";
import {deleteExpense, retrieveExpenses, retrieveOldestExpense} from "../../service/ExpenseService";
import {getOptionKeyForDate, getPrettyFormatedDate} from "../../util/DateUtil";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Col from "react-bootstrap/Col";
import Table from "react-bootstrap/Table";
import Form from "react-bootstrap/Form";

function ExpensesDetails() {
    const [expenseEntries, setExpenseEntries] = useState([]);
    const [availableMonths, setAvailableMonths] = useState([]);
    const [selectedMonth, setSelectedMonth] = useState("");

    useEffect(() => {
        retrieveOldestExpense().then(expense => {
            const oldestDate = new Date(expense.transactionDate);
            oldestDate.setDate(1);
            const currentDateOnFirst = new Date();
            currentDateOnFirst.setDate(1);

            const availableMonths = [];
            while (oldestDate <= currentDateOnFirst) {
                availableMonths.push(new Date(oldestDate.getTime()));
                oldestDate.setMonth(oldestDate.getMonth() + 1);
            }

            setAvailableMonths(availableMonths.reverse());
        });

        let now = new Date();
        let optionKey = (now.getMonth() + 1) + "." + now.getFullYear();

        setSelectedMonth(optionKey);
        retrieveExpenses(now.getMonth() + 1, now.getFullYear()).then(expenseEntries => setExpenseEntries(expenseEntries));
    }, [])

    function handleDeletion(event) {
        deleteExpense(event.target.parentElement.parentElement.id.substring(4)).then(
            () => retrieveExpenses(selectedMonth.substring(0, selectedMonth.indexOf(".")),
                selectedMonth.substring(selectedMonth.indexOf(".") + 1))
                .then(expenseEntries => setExpenseEntries(expenseEntries))
        );
    }

    function handleMonthChange(event) {
        let selectedMonth = event.target.value;
        setSelectedMonth(selectedMonth);

        retrieveExpenses(selectedMonth.substring(0, selectedMonth.indexOf(".")),
            selectedMonth.substring(selectedMonth.indexOf(".") + 1))
            .then(expenseEntries => setExpenseEntries(expenseEntries));
    }

    return (
        <div>
            <h1>Laufende Ausgaben</h1>
            <p/>

            <Container>
                <Row>
                    <Col md="9" xs="auto">
                        <Form>

                            <Row className="align-items-center">
                                <Col xs="auto">
                                    <label>Monat ausw&auml;hlen:&nbsp;&nbsp;&nbsp;</label>
                                    <Form.Select id="monthSelection" onChange={handleMonthChange}>
                                        {availableMonths.map(month => {
                                            return (<option key={getOptionKeyForDate(month)}
                                                            value={getOptionKeyForDate(month)}>
                                                {getPrettyFormatedDate(month)}
                                            </option>)
                                        })}
                                    </Form.Select>
                                </Col>
                            </Row>
                        </Form>
                    </Col>
                </Row>
            </Container>

            <Table striped bordered responsive className="text-center">
                <thead>
                <tr>
                    <th className="text-center">Datum</th>
                    <th className="text-center">Kategorie</th>
                    <th className="text-center">Kommentar</th>
                    <th className="text-center">Betrag</th>
                    <th className="text-center">L&ouml;schen</th>
                </tr>
                </thead>
                <tbody>
                {expenseEntries.map(expenseEntry => {
                    return (<tr id={"row-" + expenseEntry.id} key={expenseEntry.id}>
                        <td>
                            {new Intl.DateTimeFormat("de-AT", {
                                year: "numeric",
                                month: "numeric",
                                day: "2-digit"
                            }).format(new Date(expenseEntry.date))}
                        </td>
                        <td>{expenseEntry.comment}</td>
                        <td>{expenseEntry.description}</td>
                        <td>{new Intl.NumberFormat("de-AT", {
                            style: "currency",
                            currency: "EUR"
                        }).format(expenseEntry.amount)}</td>
                        <td><Button color="danger" className="btn-sm" onClick={handleDeletion} style={{margin: 0}}>
                            L&ouml;schen
                        </Button></td>
                    </tr>)
                })}
                </tbody>
            </Table>
        </div>
    )
}

export default ExpensesDetails;