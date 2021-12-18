import { useContext } from 'react';
import {NotificationContext} from "../../context/NotificationContext";

function useNotification() {
    const { notification, addNotification, removeNotification } = useContext(NotificationContext);
    return { notification, addNotification, removeNotification };
}

export default useNotification;