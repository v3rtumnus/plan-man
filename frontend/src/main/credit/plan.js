import React, {useEffect, useState} from "react";
import {retrieveCreditPlan} from "../../service/CreditService";
import Table from "react-bootstrap/Table";

function CreditPlan() {
    const [creditRows, setCreditRows] = useState([]);

    useEffect(() => {
        retrieveCreditPlan().then(creditRows => setCreditRows(creditRows));
    }, [])

    return (
        <div>
            <h1>Tilgungsplan</h1>
            <p/>
            <Table striped bordered hover>
                <thead>
                <tr>
                    <th>Datum</th>
                    <th>Beschreibung</th>
                    <th>Transaktionsbetrag</th>
                    <th>Neuer Kontostand</th>
                </tr>
                </thead>
                <tbody>
                {creditRows.map(creditRow => {
                    return (<tr key={creditRow.id}>
                        <td>
                            {new Intl.DateTimeFormat("de-AT", {
                                year: "numeric",
                                month: "long",
                                day: "2-digit"
                            }).format(new Date(creditRow.date))}
                        </td>
                        <td>{creditRow.description}</td>
                        <td>{new Intl.NumberFormat("de-AT", {
                            style: "currency",
                            currency: "EUR"
                        }).format(creditRow.balanceChange)}</td>
                        <td>{new Intl.NumberFormat("de-AT", {
                            style: "currency",
                            currency: "EUR"
                        }).format(creditRow.newBalance)}</td>
                    </tr>)
                })}
                </tbody>
            </Table>
        </div>
    )
}

export default CreditPlan;