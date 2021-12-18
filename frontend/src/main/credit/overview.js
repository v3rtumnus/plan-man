import React from "react";
import Alert from "react-bootstrap/Alert";
import Row from "react-bootstrap/Row";
import Col from "react-bootstrap/Col";
import Card from "react-bootstrap/Card";

function CreditOverview() {

    return (
        <div>
            <Alert variant="warning">
                Under construction - wird mit neuem Kredit finalisiert
            </Alert>
            <h1>Kredit&uuml;bersicht</h1>
            <p/>
            <Row>
                <Col size="4">
                    <Card>
                        <Card.Body>
                            <Card.Title>Restlaufzeit</Card.Title>
                            <Card.Subtitle>5 Jahre 3 Monate</Card.Subtitle>
                            <Card.Text>
                                Urspr&uuml;nglich 6 Jahre 4 Monate
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Title>Neue Sondertilgung</Card.Title>
                            <Card.Text>
                                Blabla Formular
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
            <Row>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Title>Sonderzahlungen bisher</Card.Title>
                            <Card.Text>
                                Blabla Liste
                            </Card.Text>
                        </Card.Body>
                    </Card>
                </Col>
                <Col>
                    <Card>
                        <Card.Body>
                            <Card.Title>Minimal m&ouml;gliche Rate</Card.Title>
                            <Card.Subtitle>850 Euro</Card.Subtitle>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </div>
    )
}

export default CreditOverview;