import React, {useContext, useEffect, useState} from "react";
import {ApplicationContext} from "../../context/ApplicationContext";
import {
    retrieveExpenseSummaries,
    retrieveOldestExpense,
    saveExpense
} from "../../service/ExpenseService";
import Row from "react-bootstrap/Row";
import Col from "react-bootstrap/Col";
import Card from "react-bootstrap/Card";
import Form from "react-bootstrap/Form";
import FormControl from "react-bootstrap/FormControl";
import Button from "react-bootstrap/Button";
import useNotification from "../../hooks/useNotification";
import {getOptionKeyForDate, getPrettyFormatedDate} from "../../util/DateUtil";
import Table from "react-bootstrap/Table";
import {PieChart, Pie, Legend, Cell} from "recharts";
import COLORS from "../../util/GraphUtil";

function ExpenseOverview() {
    const {addNotification} = useNotification()
    const {expenseCategories} = useContext(ApplicationContext);
    const [expenseDate, setExpenseDate] = useState(new Date().toISOString().substr(0, 10));
    const [expenseCategory, setExpenseCategory] = useState('');
    const [expenseComment, setExpenseComment] = useState('');
    const [expenseAmount, setExpenseAmount] = useState('');
    const [availableMonths, setAvailableMonths] = useState([]);
    const [selectedMonth, setSelectedMonth] = useState("");
    const [expenseSummaries, setExpenseSummaries] = useState([]);
    const [expenseSummaryMonthlySum, setExpenseSummaryMonthlySum] = useState([]);

    useEffect(() => {
        if (expenseCategories[0]) {
            setExpenseCategory(expenseCategories[0].name);
        }

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
        loadExpensesSummary(now.getMonth() + 1, now.getFullYear());
        // eslint-disable-next-line
    }, [expenseCategories])

    function handleMonthChange(event) {
        let selectedMonth = event.target.value;
        setSelectedMonth(selectedMonth);

        loadExpensesSummary(selectedMonth.substring(0, selectedMonth.indexOf(".")),
            selectedMonth.substring(selectedMonth.indexOf(".") + 1));
    }

    function loadExpensesSummary(month, year) {
        retrieveExpenseSummaries(month, year)
            .then(expenseSummariesResponse => {
                setExpenseSummaries(expenseSummariesResponse.expenseSummaries);
                setExpenseSummaryMonthlySum(expenseSummariesResponse.monthlySum);
            });
    }

    function handleSubmit(e) {
        e.preventDefault();

        saveExpense(expenseCategory, expenseAmount, expenseComment, expenseDate)
            .then(() => {
                addNotification('Ausgabe erfolgreich gespeichert', 'INFO')

                setExpenseCategory(expenseCategories[0]);
                setExpenseComment('');
                setExpenseAmount('');

                loadExpensesSummary(selectedMonth.substring(0, selectedMonth.indexOf(".")),
                    selectedMonth.substring(selectedMonth.indexOf(".") + 1));
            });
    }

    return (
        <div>
            <h1>Laufende Ausgaben</h1>
            <p/>
            <Row>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Title>Neue Ausgabe</Card.Title>
                            <Card.Text as="div">
                                <Form>
                                    <Row className="align-items-center">
                                        <Col xs="auto">
                                            <FormControl type="date" id="date" className="form-control"
                                                         value={expenseDate}
                                                         onChange={(e) => setExpenseDate(e.target.value)}/>
                                        </Col>
                                        <Col>
                                            <Form.Select id="categorySelection"
                                                         className="browser-default custom-select"
                                                         value={expenseCategory}
                                                         onChange={(e) => setExpenseCategory(e.target.value)}>
                                                {expenseCategories.map(expenseCategoryElement => {
                                                    return (<option key={expenseCategoryElement.id}
                                                                    value={expenseCategoryElement.name}>
                                                        {expenseCategoryElement.name}
                                                    </option>)
                                                })}
                                            </Form.Select>
                                        </Col>
                                        <Col>
                                            <FormControl placeholder="Kommentar" id="comment" value={expenseComment}
                                                         onChange={(e) => setExpenseComment(e.target.value)}/>
                                        </Col>
                                        <Col>
                                            <FormControl type="number" placeholder="Betrag" id="amount"
                                                         value={expenseAmount}
                                                         onChange={(e) => setExpenseAmount(e.target.value.replace(',', '.'))}/>
                                        </Col>
                                        <Col>
                                            <Button color="primary" type="submit" onClick={handleSubmit}>
                                                Speichern
                                            </Button>
                                        </Col>
                                    </Row>
                                </Form>
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
            <Row>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Text as="div">
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
                                <Table>
                                    <thead>
                                    <tr>
                                        <th scope="col">Kategorie</th>
                                        <th scope="col">Betrag</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {expenseSummaries.map(expenseSummary => {
                                        return (<tr id={"row-" + expenseSummary.category} key={expenseSummary.category}>
                                            <td>{expenseSummary.category}</td>
                                            <td>{new Intl.NumberFormat("de-AT", {
                                                style: "currency",
                                                currency: "EUR"
                                            }).format(expenseSummary.amount)}</td>
                                        </tr>)
                                    })}
                                    </tbody>
                                    {expenseSummaryMonthlySum && <tfoot>
                                    <tr>
                                        <th>Summe</th>
                                        <td>{new Intl.NumberFormat("de-AT", {
                                            style: "currency",
                                            currency: "EUR"
                                        }).format(expenseSummaryMonthlySum)}</td>
                                    </tr>
                                    </tfoot>}
                                </Table>
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Text as="div">
                                <PieChart  width={730} height={250}>
                                    <Pie data={expenseSummaries} dataKey="amount" nameKey="category">
                                        {expenseSummaries.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Legend verticalAlign="bottom" align="center" />
                                </PieChart>
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </div>
    )
}

export default ExpenseOverview;