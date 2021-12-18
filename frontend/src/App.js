import React from 'react';
import {BrowserRouter as Router, Switch,} from 'react-router-dom';
import routes from './config/routes.js';
import AuthorizationContextProvider from './context/AuthorizationContext'
import AppRoute from './components/AppRoute';
import NotificationContextProvider from "./context/NotificationContext";
import Notification from "./components/Notification/Notification";
import LocationChangeComponent from "./components/LocationChangeComponent/LocationChangeComponent";
import Header from "./components/Header/Header";
import './App.css'
import ApplicationContextProvider from "./context/ApplicationContext";

function App() {
    return (
        <NotificationContextProvider>
            <AuthorizationContextProvider>
                <ApplicationContextProvider>
                    <Router>
                        <Header/>
                        <Notification/>
                        <p/>
                        <div className='container'>
                            <Switch>
                                {routes.map((route) => (
                                    <AppRoute
                                        key={route.path}
                                        path={route.path}
                                        component={route.component}
                                        isPrivate={route.isPrivate}
                                    />
                                ))}
                            </Switch>
                        </div>
                        <LocationChangeComponent/>
                    </Router></ApplicationContextProvider>
            </AuthorizationContextProvider>
        </NotificationContextProvider>
    );
}

export default App;
