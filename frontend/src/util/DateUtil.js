export function getPrettyFormatedDate(date) {
    return new Intl.DateTimeFormat("de-AT", {
        year: "numeric",
        month: "long"
    }).format(new Date(date))
}

export function getOptionKeyForDate(date) {
    return new Intl.DateTimeFormat("de-AT", {
        year: "numeric",
        month: "numeric"
    }).format(new Date(date))
}