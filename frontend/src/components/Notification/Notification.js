import React from "react";
import useNotification from "../../hooks/useNotification";
import Alert from "react-bootstrap/Alert";

const Notification = () => {
    const { notification } = useNotification();

    if (!notification) {
        return null;
    }

    const variant = notification && notification.type === 'ERROR' ? 'danger' : 'primary';

    return (
        <Alert variant={variant}>
            {notification.message}
        </Alert >
    )
}

export default Notification