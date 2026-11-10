// A date more than 7 days ahead is usually a typo (e.g. 2027 instead of 2026): ask before saving.
function confirmExpenseDates(dates) {
    var limit = new Date();
    limit.setHours(0, 0, 0, 0);
    limit.setDate(limit.getDate() + 7);
    var future = dates.filter(function (value) {
        var parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec((value || '').trim());
        return parts && new Date(+parts[1], parts[2] - 1, +parts[3]) > limit;
    });
    if (future.length === 0) {
        return true;
    }
    var shown = future.map(function (value) {
        var p = value.trim().split('-');
        return p[2] + '.' + p[1] + '.' + p[0];
    }).join(', ');
    return confirm('Das Datum ' + shown + ' liegt mehr als eine Woche in der Zukunft. Trotzdem speichern?');
}
