import React, {useContext, useState} from 'react';

import {AuthorizationContext} from "../../context/AuthorizationContext";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Col from "react-bootstrap/Col";
import FormControl from "react-bootstrap/FormControl";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";

function Login(props) {

    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')

    const {authActions} = useContext(AuthorizationContext)

    const handleLogin = async (e) => {
        e.preventDefault()

        try {
            let response = await authActions.authenticate(username, password)
            if (!response.user) return
            props.history.push('/dashboard')
        } catch (error) {
            console.log(error)
        }
    }

    return (
        <div>
            <h1>Login</h1>
            <Container>
                <Row>
                    <Col md="5">
                        <Form>
                            <Form.Group className="mb-3" >
                                <FormControl placeholder="Benutzername" icon="user"
                                             onChange={(e) => setUsername(e.target.value)}/>
                            </Form.Group>
                            <Form.Group className="mb-3" >
                                <FormControl placeholder="Passwort" type="password" icon="lock"
                                             onChange={(e) => setPassword(e.target.value)}/>
                            </Form.Group>
                            <Form.Group className="mb-3" >
                                <Button color="primary" type="submit" onClick={handleLogin}>
                                    Login
                                </Button>
                            </Form.Group>
                        </Form>
                    </Col>
                </Row>
            </Container>
        </div>
    )
}

export default Login;
