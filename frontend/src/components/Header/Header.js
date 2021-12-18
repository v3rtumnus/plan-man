import React, {useContext} from "react";
import {AuthorizationContext} from "../../context/AuthorizationContext";
import Navbar from "react-bootstrap/Navbar";
import Nav from "react-bootstrap/Nav";
import NavDropdown from "react-bootstrap/NavDropdown";
import Container from "react-bootstrap/Container";

function Header() {
    const {authorizationData} = useContext(AuthorizationContext)

    return (
        <Navbar bg="primary" variant="dark">
            <Container>
                <Navbar.Brand href="/">Plan-Man</Navbar.Brand>
                {Boolean(authorizationData.token) && <Navbar.Collapse id="responsive-navbar-nav">
                    <Nav>
                        <NavDropdown title="Ausgaben" id="basic-nav-dropdown">
                            <NavDropdown.Item href="/expenses/overview">&Uuml;bersicht</NavDropdown.Item>
                            <NavDropdown.Item href="/expenses/detail">Detailansicht</NavDropdown.Item>
                        </NavDropdown>
                        <Nav.Link href="/monthlyBalance">Monatsbilanz</Nav.Link>
                        <NavDropdown title="Kredit" id="basic-nav-dropdown">
                            <NavDropdown.Item href="/credit/overview">&Uuml;bersicht</NavDropdown.Item>
                            <NavDropdown.Item href="/credit/plan">Tilgungsplan</NavDropdown.Item>
                        </NavDropdown>
                        <NavDropdown title="Finanzen" id="basic-nav-dropdown">
                            <NavDropdown.Item href="/finances/overview">&Uuml;bersicht</NavDropdown.Item>
                            <NavDropdown.Item href="/finances/shares">Aktien</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                    <Nav>
                        <NavDropdown title={
                            <span><i className="fa fa-user fa-fw"/>User</span>
                        } id="basic-nav-dropdown">
                            <NavDropdown.Item href="/settings">Einstellungen</NavDropdown.Item>
                            <NavDropdown.Item href="/logout">Logout</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                </Navbar.Collapse>}
            </Container>
        </Navbar>
    );
}

export default Header;
